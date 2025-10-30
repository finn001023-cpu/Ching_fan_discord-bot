import asyncio
import json
import os
import random
from datetime import datetime, timedelta

import discord
from discord import app_commands
from discord.ext import commands
import pytz


STATE_FILE = "XXX.json"
TZ_TAIPEI = pytz.timezone("Asia/Taipei")
MUTE_MINUTES = 1440
LEDGER_FILE = "XXX.json"
EXCLUDED_USER_ID = 0
EXEMPT_ROLE_ID = 0
ANNOUNCE_CHANNEL_ID = 0


def _load_state() -> dict:
    try:
        if os.path.exists(STATE_FILE):
            with open(STATE_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
    except Exception:
        pass
    return {}


def _save_state(data: dict) -> None:
    try:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def _load_ledger() -> list:
    try:
        if os.path.exists(LEDGER_FILE):
            with open(LEDGER_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
    except Exception:
        pass
    return []


def _append_ledger(entry: dict) -> None:
    data = _load_ledger()
    data.append(entry)
    try:
        with open(LEDGER_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def _today_utc8_str() -> str:
    return datetime.now(TZ_TAIPEI).strftime("%Y-%m-%d")


def _seconds_until_next_midnight_utc8() -> float:
    now = datetime.now(TZ_TAIPEI)
    tomorrow = now + timedelta(days=1)
    next_midnight = TZ_TAIPEI.localize(
        datetime(tomorrow.year, tomorrow.month, tomorrow.day, 0, 0, 0)
    )
    return max(1.0, (next_midnight - now).total_seconds())


class BettingMute(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self._task_midnight = asyncio.create_task(self._scheduler_midnight())

    def cog_unload(self):
        if hasattr(self, "_task_midnight") and self._task_midnight and not self._task_midnight.done():
            self._task_midnight.cancel()

    async def _scheduler_midnight(self):
        await self.bot.wait_until_ready()
        while not self.bot.is_closed():
            try:
                sleep_seconds = _seconds_until_next_midnight_utc8()
                await asyncio.sleep(sleep_seconds)
                await self._run_daily_all_guilds()
            except asyncio.CancelledError:
                break
            except Exception:
                await asyncio.sleep(60)

    async def _run_daily_all_guilds(self):
        state = _load_state()
        today = _today_utc8_str()

        for g in list(state.keys()):
            gconf = state.get(g) or {}
            if not gconf.get("enabled"):
                continue
            if gconf.get("last_executed") == today:
                continue

            guild = self.bot.get_guild(int(g))
            if not guild:
                continue

            try:
                success = await self._run_for_guild(guild, gconf)
                if success:
                    gconf["last_executed"] = today
                    state[g] = gconf
            except Exception:
                continue

        _save_state(state)

    async def _run_for_guild(self, guild: discord.Guild, gconf: dict) -> bool:
        channel = guild.get_channel(ANNOUNCE_CHANNEL_ID)

        me: discord.Member | None = guild.me
        if not me or not me.guild_permissions.moderate_members:
            return False

        candidates: list[discord.Member] = []
        async for m in guild.fetch_members(limit=None):
            if m.bot:
                continue
            if m.id == EXCLUDED_USER_ID:
                continue
            try:
                if any(r.id == EXEMPT_ROLE_ID for r in m.roles):
                    continue
            except Exception:
                pass
            candidates.append(m)

        if not candidates:
            return False

        random.shuffle(candidates)
        winner: discord.Member | None = None
        for m in candidates:
            try:
                until = discord.utils.utcnow() + timedelta(minutes=MUTE_MINUTES)
                await m.timeout(until, reason="每日隨機禁言")
                winner = m
                break
            except Exception:
                continue

        if not winner:
            return False

        reward_text = "找管理員領取"
        reward_amount = 2000

        embed = discord.Embed(
            title="公告",
            description=(
                f"恭喜{winner.mention}成為今天的幸運兒\n"
                f"可以{reward_text}你的 '{reward_amount}' [自訂義幣值] 獎賞"
            ),
            color=0xFFB7C5
        )
        try:
            content = f"<@&{EXEMPT_ROLE_ID}>"
            if channel:
                await channel.send(content=content, embed=embed, allowed_mentions=discord.AllowedMentions(roles=True))
            elif guild.system_channel:
                await guild.system_channel.send(content=content, embed=embed, allowed_mentions=discord.AllowedMentions(roles=True))
        except Exception:
            pass

        _append_ledger({
            "date": _today_utc8_str(),
            "guild_id": guild.id,
            "user_id": winner.id,
            "user_name": winner.display_name,
            "channel_id": channel.id if channel else (guild.system_channel.id if guild.system_channel else None),
            "muted_minutes": MUTE_MINUTES,
            "timestamp": datetime.now(TZ_TAIPEI).isoformat(),
            "reason": "random"
        })

        return True

    async def simulate_announce(self, guild: discord.Guild, channel: discord.abc.Messageable, testing: bool = False):
        channel = guild.get_channel(ANNOUNCE_CHANNEL_ID) or (guild.system_channel if guild.system_channel else None)
        if channel is None:
            return
        candidates: list[discord.Member] = []
        async for m in guild.fetch_members(limit=None):
            if m.bot:
                continue
            if m.id == EXCLUDED_USER_ID:
                continue
            candidates.append(m)
        if not candidates:
            return
        winner = random.choice(candidates)
        reward_text = "找管理員領取"
        reward_amount = 2000
        target_text = "{人員}" if testing else winner.mention
        embed = discord.Embed(
            title="公告",
            description=(
                f"恭喜{target_text}成為今天的幸運兒\n"
                f"可以{reward_text}你的 '{reward_amount}' [自訂義幣值] 獎賞"
            ),
            color=0xFFB7C5
        )
        try:
            await channel.send(content=f"<@&{EXEMPT_ROLE_ID}>", embed=embed, allowed_mentions=discord.AllowedMentions(roles=True))
        except Exception:
            pass

    @commands.Cog.listener()
    async def on_member_join(self, member: discord.Member):
        try:
            welcome_message = "[default]"
            await member.send(welcome_message)
        except discord.Forbidden:
            pass
        except Exception:
            pass

    @commands.Cog.listener()
    async def on_message(self, message: discord.Message):
        if message.author.bot or not message.guild:
            return
        if message.author.id != EXCLUDED_USER_ID:
            return
        content = message.content.strip()
        if content.startswith("!中獎訊息測試"):
            await self.simulate_announce(message.guild, message.channel, testing=True)
        elif content == "!停止":
            try:
                await message.channel.send("機器人即將關閉。")
            except Exception:
                pass
            await self.bot.close()

    @app_commands.command(name="start", description="開始每日 UTC+8 00:00 隨機禁言一人")
    @app_commands.checks.has_permissions(administrator=True)
    async def start_bet(self, interaction: discord.Interaction):
        try:
            if not interaction.response.is_done():
                await interaction.response.defer(ephemeral=True)
        except Exception:
            pass

        if not interaction.guild:
            try:
                if interaction.response.is_done():
                    await interaction.followup.send("此指令需在伺服器使用。", ephemeral=True)
                else:
                    await interaction.response.send_message("此指令需在伺服器使用。", ephemeral=True)
            except Exception:
                pass
            return

        try:
            if not interaction.user.guild_permissions.administrator:
                if interaction.response.is_done():
                    await interaction.followup.send("你沒有管理員權限，無法使用此指令。", ephemeral=True)
                else:
                    await interaction.response.send_message("你沒有管理員權限，無法使用此指令。", ephemeral=True)
                return
        except Exception:
            pass

        state = _load_state()
        g = str(interaction.guild.id)
        gconf = state.get(g) or {}
        gconf["enabled"] = True
        gconf["announce_channel_id"] = interaction.channel.id
        gconf.setdefault("last_executed", _today_utc8_str())
        state[g] = gconf
        _save_state(state)

        try:
            if interaction.response.is_done():
                await interaction.followup.send(
                    "已啟動：每日 UTC+8 00:00 隨機禁言並公告。",
                    ephemeral=True,
                )
            else:
                await interaction.response.send_message(
                    "已啟動：每日 UTC+8 00:00 隨機禁言並公告。",
                    ephemeral=True,
                )
        except Exception:
            pass

    @start_bet.error
    async def _start_bet_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        try:
            if isinstance(error, app_commands.MissingPermissions):
                await interaction.response.send_message("你沒有管理員權限，無法使用此指令。", ephemeral=True)
            else:
                if not interaction.response.is_done():
                    await interaction.response.send_message("指令執行失敗，請稍後再試或聯繫管理員。", ephemeral=True)
        except Exception:
            pass

    @app_commands.command(name="test", description="測試中獎公告（僅限指定使用者）")
    async def test_announce(self, interaction: discord.Interaction):
        if interaction.user.id != EXCLUDED_USER_ID:
            await interaction.response.send_message("你沒有權限使用此測試指令。", ephemeral=True)
            return
        try:
            await interaction.response.send_message("已發送測試公告至指定頻道。", ephemeral=True)
        except Exception:
            pass
        try:
            await self.simulate_announce(interaction.guild, interaction.channel, testing=True)
        except Exception:
            pass


async def setup(bot: commands.Bot):
    await bot.add_cog(BettingMute(bot))


if __name__ == "__main__":
    TOKEN = "YOUR_DISCORD_TOKEN"
    intents = discord.Intents.default()
    intents.guilds = True
    intents.members = True
    intents.message_content = True
    bot = commands.Bot(command_prefix='/', intents=intents, help_command=None)

    async def _setup_hook():
        await bot.add_cog(BettingMute(bot))
    bot.setup_hook = _setup_hook

    @bot.event
    async def on_ready():
        try:
            await bot.change_presence(
                status=discord.Status.dnd,
                activity=discord.Activity(
                    type=discord.ActivityType.watching,
                    name="default"
                )
            )
            await bot.tree.sync()
        except Exception:
            pass

    bot.run(TOKEN)