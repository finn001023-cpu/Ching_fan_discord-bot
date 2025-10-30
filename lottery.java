import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Bot extends ListenerAdapter {
    private static final String STATE_FILE = "XXX.json";
    private static final String LEDGER_FILE = "XXX.json";
    private static final ZoneId TZ_TAIPEI = ZoneId.of("Asia/Taipei");
    private static final int MUTE_MINUTES = 1440;
    private static final long EXCLUDED_USER_ID = 0L;
    private static final long EXEMPT_ROLE_ID = 0L;
    private static final long ANNOUNCE_CHANNEL_ID = 0L;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, Object> state = new HashMap<>();
    
    public static void main(String[] args) throws LoginException {
        String token = "YOUR_DISCORD_TOKEN";
        
        JDABuilder builder = JDABuilder.createDefault(token);
        builder.enableIntents(
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MESSAGES
        );
        builder.disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI);
        builder.addEventListeners(new Bot());
        builder.setActivity(net.dv8tion.jda.api.entities.Activity.watching("default"));
        
        JDA jda = builder.build();
        
        jda.updateCommands().addCommands(
            Commands.slash("start", "開始每日 UTC+8 00:00 隨機禁言一人")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
            Commands.slash("test", "測試中獎公告（僅限指定使用者）")
        ).queue();
    }
    
    public Bot() {
        loadState();
        scheduleDailyTask();
    }
    
    @SuppressWarnings("unchecked")
    private void loadState() {
        try {
            if (Files.exists(Paths.get(STATE_FILE))) {
                String content = new String(Files.readAllBytes(Paths.get(STATE_FILE)));
                state.putAll((Map<String, Object>) new com.google.gson.Gson().fromJson(content, Map.class));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void saveState() {
        try {
            Files.write(Paths.get(STATE_FILE), new com.google.gson.Gson().toJson(state).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadLedger() {
        try {
            if (Files.exists(Paths.get(LEDGER_FILE))) {
                String content = new String(Files.readAllBytes(Paths.get(LEDGER_FILE)));
                return new com.google.gson.Gson().fromJson(content, List.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    private void appendLedger(Map<String, Object> entry) {
        List<Map<String, Object>> data = loadLedger();
        data.add(entry);
        try {
            Files.write(Paths.get(LEDGER_FILE), new com.google.gson.Gson().toJson(data).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String getTodayUTC8() {
        return LocalDateTime.now(TZ_TAIPEI).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    private long getSecondsUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now(TZ_TAIPEI);
        LocalDateTime tomorrowMidnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0);
        Duration duration = Duration.between(now, tomorrowMidnight);
        return Math.max(1, duration.getSeconds());
    }
    
    private void scheduleDailyTask() {
        long initialDelay = getSecondsUntilNextMidnight();
        scheduler.scheduleAtFixedRate(this::runDailyTask, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }
    
    private void runDailyTask() {
        String today = getTodayUTC8();
        
        for (String guildId : state.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> gconf = (Map<String, Object>) state.getOrDefault(guildId, new HashMap<>());
            
            if (!Boolean.TRUE.equals(gconf.get("enabled"))) continue;
            if (today.equals(gconf.get("last_executed"))) continue;
            
            JDA jda = net.dv8tion.jda.api.JDA.getShardById(0);
            if (jda == null) continue;
            
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) continue;
            
            try {
                boolean success = runForGuild(guild, gconf);
                if (success) {
                    gconf.put("last_executed", today);
                    state.put(guildId, gconf);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        saveState();
    }
    
    private boolean runForGuild(Guild guild, Map<String, Object> gconf) {
        TextChannel channel = guild.getTextChannelById(ANNOUNCE_CHANNEL_ID);
        
        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)) {
            return false;
        }
        
        List<Member> candidates = new ArrayList<>();
        for (Member member : guild.loadMembers().get()) {
            if (member.getUser().isBot()) continue;
            if (member.getIdLong() == EXCLUDED_USER_ID) continue;
            
            boolean hasExemptRole = member.getRoles().stream()
                .anyMatch(role -> role.getIdLong() == EXEMPT_ROLE_ID);
            if (hasExemptRole) continue;
            
            candidates.add(member);
        }
        
        if (candidates.isEmpty()) return false;
        
        Collections.shuffle(candidates);
        Member winner = null;
        for (Member member : candidates) {
            try {
                member.timeoutFor(Duration.ofMinutes(MUTE_MINUTES)).reason("每日隨機禁言").queue();
                winner = member;
                break;
            } catch (Exception e) {
                continue;
            }
        }
        
        if (winner == null) return false;
        
        String rewardText = "找管理員領取";
        int rewardAmount = 2000;
        
        net.dv8tion.jda.api.entities.MessageEmbed embed = new net.dv8tion.jda.api.entities.MessageEmbed.Builder()
            .setTitle("公告")
            .setDescription(String.format(
                "恭喜%s成為今天的幸運兒\n可以%s你的 '%d' [自訂義幣值] 獎賞",
                winner.getAsMention(), rewardText, rewardAmount
            ))
            .setColor(0xFFB7C5)
            .build();
        
        try {
            String content = String.format("<@&%d>", EXEMPT_ROLE_ID);
            if (channel != null) {
                channel.sendMessage(content).setEmbeds(embed).queue();
            } else if (guild.getSystemChannel() != null) {
                guild.getSystemChannel().sendMessage(content).setEmbeds(embed).queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        Map<String, Object> ledgerEntry = new HashMap<>();
        ledgerEntry.put("date", getTodayUTC8());
        ledgerEntry.put("guild_id", guild.getId());
        ledgerEntry.put("user_id", winner.getId());
        ledgerEntry.put("user_name", winner.getEffectiveName());
        ledgerEntry.put("channel_id", channel != null ? channel.getId() : 
            (guild.getSystemChannel() != null ? guild.getSystemChannel().getId() : null));
        ledgerEntry.put("muted_minutes", MUTE_MINUTES);
        ledgerEntry.put("timestamp", LocalDateTime.now(TZ_TAIPEI).format(DateTimeFormatter.ISO_DATE_TIME));
        ledgerEntry.put("reason", "random");
        
        appendLedger(ledgerEntry);
        return true;
    }
    
    private void simulateAnnounce(Guild guild, MessageChannel channel, boolean testing) {
        TextChannel announceChannel = guild.getTextChannelById(ANNOUNCE_CHANNEL_ID);
        if (announceChannel == null) {
            announceChannel = guild.getSystemChannel();
        }
        if (announceChannel == null) return;
        
        List<Member> candidates = new ArrayList<>();
        for (Member member : guild.loadMembers().get()) {
            if (member.getUser().isBot()) continue;
            if (member.getIdLong() == EXCLUDED_USER_ID) continue;
            candidates.add(member);
        }
        
        if (candidates.isEmpty()) return;
        
        Member winner = candidates.get(new Random().nextInt(candidates.size()));
        String rewardText = "找管理員領取";
        int rewardAmount = 2000;
        String targetText = testing ? "{人員}" : winner.getAsMention();
        
        net.dv8tion.jda.api.entities.MessageEmbed embed = new net.dv8tion.jda.api.entities.MessageEmbed.Builder()
            .setTitle("公告")
            .setDescription(String.format(
                "恭喜%s成為今天的幸運兒\n可以%s你的 '%d' [自訂義幣值] 獎賞",
                targetText, rewardText, rewardAmount
            ))
            .setColor(0xFFB7C5)
            .build();
        
        try {
            announceChannel.sendMessage(String.format("<@&%d>", EXEMPT_ROLE_ID))
                .setEmbeds(embed)
                .queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            event.getUser().openPrivateChannel().queue(privateChannel -> {
                privateChannel.sendMessage("[default]").queue();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;
        if (event.getAuthor().getIdLong() != EXCLUDED_USER_ID) return;
        
        String content = event.getMessage().getContentRaw().trim();
        if (content.startsWith("!中獎訊息測試")) {
            simulateAnnounce(event.getGuild(), event.getChannel(), true);
        } else if (content.equals("!停止")) {
            try {
                event.getChannel().sendMessage("機器人即將關閉。").queue();
            } catch (Exception e) {
                e.printStackTrace();
            }
            event.getJDA().shutdown();
        }
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "start":
                handleStartCommand(event);
                break;
            case "test":
                handleTestCommand(event);
                break;
        }
    }
    
    private void handleStartCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("此指令需在伺服器使用。").setEphemeral(true).queue();
            return;
        }
        
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("你沒有管理員權限，無法使用此指令。").setEphemeral(true).queue();
            return;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> gconf = (Map<String, Object>) state.getOrDefault(event.getGuild().getId(), new HashMap<>());
        gconf.put("enabled", true);
        gconf.put("announce_channel_id", event.getChannel().getId());
        gconf.putIfAbsent("last_executed", getTodayUTC8());
        state.put(event.getGuild().getId(), gconf);
        saveState();
        
        event.reply("已啟動：每日 UTC+8 00:00 隨機禁言並公告。").setEphemeral(true).queue();
    }
    
    private void handleTestCommand(SlashCommandInteractionEvent event) {
        if (event.getUser().getIdLong() != EXCLUDED_USER_ID) {
            event.reply("你沒有權限使用此測試指令。").setEphemeral(true).queue();
            return;
        }
        
        event.reply("已發送測試公告至指定頻道。").setEphemeral(true).queue();
        simulateAnnounce(event.getGuild(), event.getChannel(), true);
    }
}