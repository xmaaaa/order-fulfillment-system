package com.xm.service.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.Recording;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Dial;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.voice.Say;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@SpringBootApplication
@RestController
@RequestMapping("/api")
public class TwilioDemo {

    // ==================== 配置 ====================
    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    @Value("${twilio.webhook-base-url:http://localhost:8888}")
    private String webhookBaseUrl;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
        log.info("✅ Twilio initialized. Account: {}", accountSid);
    }

    public static void main(String[] args) {
        SpringApplication.run(TwilioDemo.class, args);
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════════╗
            ║  🚀 Twilio Demo Started!                                  ║
            ║                                                           ║
            ║  SMS:   POST /api/sms/send                               ║
            ║  Voice: POST /api/voice/call                             ║
            ║  Video: POST /api/video/room                             ║
            ║         POST /api/video/token                            ║
            ╚═══════════════════════════════════════════════════════════╝
            """);
    }

    // ==================== SMS 短信 ====================

    /**
     * 发送短信
     * POST /api/sms/send
     * Body: {"to": "+1234567890", "message": "Hello!"}
     */
    @PostMapping("/sms/send")
    public ResponseEntity<Map<String, Object>> sendSms(@RequestBody SmsRequest request) {
        try {
            log.info("📱 Sending SMS to: {}", request.getTo());

            // SDK 11.x 使用静态方法构建
            Message message;
            
            if (request.getMediaUrl() != null && !request.getMediaUrl().isBlank()) {
                // MMS 带图片
                message = Message.creator(
                        new PhoneNumber(request.getTo()),
                        new PhoneNumber(twilioPhoneNumber),
                        request.getMessage())
                    .setMediaUrl(List.of(URI.create(request.getMediaUrl())))
                    //.setStatusCallback(URI.create(webhookBaseUrl + "/api/webhook/sms/status"))
                    .create();
            } else {
                // 普通短信
                message = Message.creator(
                        accountSid,
                        new PhoneNumber(request.getTo()),
                        new PhoneNumber(twilioPhoneNumber),
                        request.getMessage())
                    //.setStatusCallback(URI.create(webhookBaseUrl + "/api/webhook/sms/status"))
                    .create();
            }

            log.info("✅ SMS sent. SID: {}, Status: {}", message.getSid(), message.getStatus());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "sid", message.getSid(),
                "status", message.getStatus().toString(),
                "to", message.getTo(),
                "from", message.getFrom().toString()
            ));
        } catch (Exception e) {
            log.error("❌ SMS failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 发送预约提醒
     */
    @PostMapping("/sms/booking-reminder")
    public ResponseEntity<Map<String, Object>> sendBookingReminder(
            @RequestParam String to,
            @RequestParam String customerName,
            @RequestParam String serviceName,
            @RequestParam String appointmentTime,
            @RequestParam String staffName) {

        String messageText = String.format(
            "Hi %s! 🗓️ Reminder: Your %s appointment is scheduled for %s with %s. " +
            "Reply YES to confirm or NO to cancel.",
            customerName, serviceName, appointmentTime, staffName
        );

        SmsRequest request = new SmsRequest();
        request.setTo(to);
        request.setMessage(messageText);
        return sendSms(request);
    }

    /**
     * 查询消息状态
     */
    @GetMapping("/sms/{messageSid}")
    public ResponseEntity<Map<String, Object>> getSmsStatus(@PathVariable String messageSid) {
        try {
            Message msg = Message.fetcher(messageSid).fetch();
            return ResponseEntity.ok(Map.of(
                "sid", msg.getSid(),
                "status", msg.getStatus().toString(),
                "to", msg.getTo(),
                "dateSent", Objects.toString(msg.getDateSent(), ""),
                "errorCode", Objects.toString(msg.getErrorCode(), ""),
                "errorMessage", Objects.toString(msg.getErrorMessage(), "")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== Voice 语音 ====================

    /**
     * 发起外呼
     * POST /api/voice/call
     * Body: {"to": "+1234567890", "message": "Hello, this is a test call"}
     */
    @PostMapping("/voice/call")
    public ResponseEntity<Map<String, Object>> makeCall(@RequestBody VoiceRequest request) {
        try {
            log.info("📞 Making call to: {}", request.getTo());

            String twimlUrl = "https://handler.twilio.com/twiml/EH38a643bd309b540ecd2f3acf56d9b3ee";

            // SDK 11.x 的 Call.creator
            Call call = Call.creator(
                    accountSid,
                    new PhoneNumber(request.getTo()),
                    new PhoneNumber(twilioPhoneNumber),
                    URI.create(twimlUrl))
                .setRecord(request.isRecord())
                //.setStatusCallback(URI.create(webhookBaseUrl + "/api/webhook/voice/status"))
                .setTimeout(request.getTimeout() > 0 ? request.getTimeout() : 30)
                .create();

            log.info("✅ Call initiated. SID: {}, Status: {}", call.getSid(), call.getStatus());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "sid", call.getSid(),
                "status", call.getStatus().toString(),
                "to", call.getTo(),
                "from", call.getFrom()
            ));
        } catch (Exception e) {
            log.error("❌ Call failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 预约确认电话
     */
    @PostMapping("/voice/booking-call")
    public ResponseEntity<Map<String, Object>> makeBookingCall(
            @RequestParam String to,
            @RequestParam String customerName,
            @RequestParam String serviceName,
            @RequestParam String appointmentTime) {

        try {
            String twimlUrl = String.format(
                "%s/api/webhook/voice/booking-confirm?name=%s&service=%s&time=%s",
                webhookBaseUrl,
                java.net.URLEncoder.encode(customerName, "UTF-8"),
                java.net.URLEncoder.encode(serviceName, "UTF-8"),
                java.net.URLEncoder.encode(appointmentTime, "UTF-8")
            );

            Call call = Call.creator(
                    accountSid,
                    new PhoneNumber(to),
                    new PhoneNumber(twilioPhoneNumber),
                    URI.create(twimlUrl))
                .setRecord(true)
                .create();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "sid", call.getSid(),
                "status", call.getStatus().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询通话状态
     */
    @GetMapping("/voice/{callSid}")
    public ResponseEntity<Map<String, Object>> getCallStatus(@PathVariable String callSid) {
        try {
            Call call = Call.fetcher(callSid).fetch();
            return ResponseEntity.ok(Map.of(
                "sid", call.getSid(),
                "status", call.getStatus().toString(),
                "duration", Objects.toString(call.getDuration(), "0"),
                "direction", call.getDirection().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 结束通话
     */
    @PostMapping("/voice/{callSid}/end")
    public ResponseEntity<Map<String, Object>> endCall(@PathVariable String callSid) {
        try {
            Call call = Call.updater(callSid)
                .setStatus(Call.UpdateStatus.COMPLETED)
                .update();
            return ResponseEntity.ok(Map.of("success", true, "status", call.getStatus().toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取通话录音
     */
    @GetMapping("/voice/{callSid}/recordings")
    public ResponseEntity<List<Map<String, Object>>> getRecordings(@PathVariable String callSid) {
        try {
            List<Map<String, Object>> recordings = StreamSupport.stream(
                Recording.reader().setCallSid(callSid).read().spliterator(), false
            ).map(r -> Map.<String, Object>of(
                "sid", r.getSid(),
                "duration", r.getDuration(),
                "status", r.getStatus().toString(),
                "url", "https://api.twilio.com" + r.getUri().replace(".json", ".mp3")
            )).collect(Collectors.toList());

            return ResponseEntity.ok(recordings);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // ==================== Webhooks 回调处理 ====================

    /** SMS 状态回调 */
    @PostMapping("/webhook/sms/status")
    public ResponseEntity<Void> handleSmsStatus(@RequestParam Map<String, String> params) {
        log.info("📱 SMS Status: SID={}, Status={}", 
            params.get("MessageSid"), params.get("MessageStatus"));
        return ResponseEntity.ok().build();
    }

    /** SMS 入站消息 */
    @PostMapping(value = "/webhook/sms/incoming", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleIncomingSms(@RequestParam Map<String, String> params) {
        String from = params.get("From");
        String body = params.get("Body");
        log.info("📱 Incoming SMS from {}: {}", from, body);

        // 自动回复
        String reply = switch (body != null ? body.toUpperCase().trim() : "") {
            case "YES", "CONFIRM" -> "Thank you! Your appointment is confirmed. 🎉";
            case "NO", "CANCEL" -> "Your appointment has been cancelled.";
            case "HELP" -> "Reply YES to confirm, NO to cancel.";
            default -> "Thanks for your message! Reply HELP for options.";
        };

        String twiml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response><Message>%s</Message></Response>
            """.formatted(reply);

        return ResponseEntity.ok(twiml);
    }

    /** Voice: 简单朗读文字 */
    @PostMapping(value = "/webhook/voice/say", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleVoiceSay(@RequestParam String text) {
        VoiceResponse response = new VoiceResponse.Builder()
            .say(new Say.Builder(text).voice(Say.Voice.POLLY_JOANNA).build())
            .build();
        return ResponseEntity.ok(response.toXml());
    }

    /** Voice: IVR 菜单 */
    @PostMapping(value = "/webhook/voice/answer", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleVoiceAnswer() {
        Say say = new Say.Builder("Hello! Welcome to TeaTime Spa.")
                .voice(Say.Voice.POLLY_JOANNA)
                .build();

        Gather gather = new Gather.Builder()
                .numDigits(1)
                .action("/api/webhook/voice/menu")
                .build();

        VoiceResponse response = new VoiceResponse.Builder()
                .say(say)
                .gather(gather)
                .build();
        return ResponseEntity.ok(response.toXml());
    }

    /** Voice: 菜单响应 */
    @PostMapping(value = "/webhook/voice/menu", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleVoiceMenu(@RequestParam String Digits) {
        VoiceResponse.Builder builder = new VoiceResponse.Builder();

        switch (Digits) {
            case "1" -> builder.say(new Say.Builder(
                "To book an appointment, please visit our website or call during business hours. Goodbye!")
                .voice(Say.Voice.POLLY_JOANNA).build());
            case "2" -> builder.say(new Say.Builder(
                "We are open Monday through Saturday, 9 AM to 7 PM. Goodbye!")
                .voice(Say.Voice.POLLY_JOANNA).build());
            case "0" -> builder.say(new Say.Builder(
                "Please hold while we connect you.").voice(Say.Voice.POLLY_JOANNA).build())
                .dial(new Dial.Builder().number(new Number.Builder(twilioPhoneNumber).build()).build());
            default -> builder.say(new Say.Builder("Invalid option. Goodbye!")
                .voice(Say.Voice.POLLY_JOANNA).build());
        }

        return ResponseEntity.ok(builder.build().toXml());
    }

    /** Voice: 预约确认 */
    @PostMapping(value = "/webhook/voice/booking-confirm", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleBookingConfirm(
            @RequestParam(defaultValue = "Customer") String name,
            @RequestParam(defaultValue = "appointment") String service,
            @RequestParam(defaultValue = "soon") String time) {

        VoiceResponse response = new VoiceResponse.Builder()
            .say(new Say.Builder(String.format(
                "Hello %s! This is TeaTime Spa confirming your %s for %s. " +
                "Press 1 to confirm. Press 2 to cancel.", name, service, time))
                .voice(Say.Voice.POLLY_JOANNA).build())
            .gather(new Gather.Builder()
                .numDigits(1)
                .action("/api/webhook/voice/booking-response")
                .timeout(10)
                .build())
            .say(new Say.Builder("No response received. Goodbye!")
                .voice(Say.Voice.POLLY_JOANNA).build())
            .build();

        return ResponseEntity.ok(response.toXml());
    }

    /** Voice: 预约响应 */
    @PostMapping(value = "/webhook/voice/booking-response", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleBookingResponse(@RequestParam String Digits) {
        String message = switch (Digits) {
            case "1" -> "Thank you! Your appointment is confirmed. See you soon!";
            case "2" -> "Your appointment has been cancelled. Please visit our website to reschedule.";
            default -> "Invalid selection. Goodbye!";
        };

        VoiceResponse response = new VoiceResponse.Builder()
            .say(new Say.Builder(message).voice(Say.Voice.POLLY_JOANNA).build())
            .build();

        return ResponseEntity.ok(response.toXml());
    }

    /** Voice: 状态回调 */
    @PostMapping("/webhook/voice/status")
    public ResponseEntity<Void> handleVoiceStatus(@RequestParam Map<String, String> params) {
        log.info("📞 Call Status: SID={}, Status={}, Duration={}s",
            params.get("CallSid"), params.get("CallStatus"), params.get("CallDuration"));
        return ResponseEntity.ok().build();
    }

    /** Voice: 录音完成 */
    @PostMapping("/webhook/voice/recording")
    public ResponseEntity<Void> handleRecording(@RequestParam Map<String, String> params) {
        log.info("🎙️ Recording: SID={}, URL={}, Duration={}s",
            params.get("RecordingSid"), params.get("RecordingUrl"), params.get("RecordingDuration"));
        return ResponseEntity.ok().build();
    }

    /** Video: 状态回调 */
    @PostMapping("/webhook/video/status")
    public ResponseEntity<Void> handleVideoStatus(@RequestBody Map<String, Object> payload) {
        log.info("🎥 Video Event: Room={}, Event={}",
            payload.get("RoomName"), payload.get("StatusCallbackEvent"));
        return ResponseEntity.ok().build();
    }

    // ==================== DTOs ====================

    @Data
    public static class SmsRequest {
        private String to;
        private String message;
        private String mediaUrl;  // 可选，MMS 用
    }

    @Data
    public static class VoiceRequest {
        private String to;
        private String message;   // 可选，朗读的文字
        private boolean record;
        private int timeout = 30;
    }

    @Data
    public static class VideoRoomRequest {
        private String roomName;
        private String type = "group";  // go, peer-to-peer, group, group-small
        private int maxParticipants = 10;
        private boolean recordParticipants = false;
    }

    @Data
    public static class VideoTokenRequest {
        private String identity;
        private String roomName;
        private int ttl = 3600;
    }
}