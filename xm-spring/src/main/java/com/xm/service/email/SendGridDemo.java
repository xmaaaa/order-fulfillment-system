package com.xm.service.email;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootApplication
@RestController
@RequestMapping("/api/email")
public class SendGridDemo {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Value("${sendgrid.from-email:noreply@yourdomain.com}")
    private String fromEmail;

    @Value("${sendgrid.from-name:TeaTime Spa}")
    private String fromName;

    private SendGrid sendGrid;

    @PostConstruct
    public void init() {
        sendGrid = new SendGrid(apiKey);
        log.info("✅ SendGrid initialized. From: {} <{}>", fromName, fromEmail);
    }

    public static void main(String[] args) {
        SpringApplication.run(SendGridDemo.class, args);
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════════╗
            ║  🚀 SendGrid Email Demo Started!                          ║
            ║                                                           ║
            ║  Simple:     POST /api/email/send                         ║
            ║  Template:   POST /api/email/send-template                ║
            ║  Booking:    POST /api/email/booking-confirmation         ║
            ║  Reminder:   POST /api/email/booking-reminder             ║
            ║  Receipt:    POST /api/email/payment-receipt              ║
            ║  Bulk:       POST /api/email/send-bulk                    ║
            ╚═══════════════════════════════════════════════════════════╝
            """);
    }

    // ==================== 基础邮件发送 ====================

    /**
     * 发送简单邮件
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmail(@RequestBody EmailRequest request) {
        try {
            log.info("📧 Sending email to: {}", request.getTo());

            Email from = new Email(fromEmail, fromName);
            Email to = new Email(request.getTo(), request.getToName());
            
            Mail mail = new Mail();
            mail.setFrom(from);
            mail.setSubject(request.getSubject());
            
            Personalization personalization = new Personalization();
            personalization.addTo(to);
            mail.addPersonalization(personalization);

            // ⚠️ SendGrid 要求顺序: text/plain 必须在 text/html 之前
            if (request.getTextContent() != null) {
                mail.addContent(new Content("text/plain", request.getTextContent()));
            }
            if (request.getHtmlContent() != null) {
                mail.addContent(new Content("text/html", request.getHtmlContent()));
            } else if (request.getTextContent() != null) {
                // 没有 HTML 时，用纯文本包一层
                mail.addContent(new Content("text/html", "<p>" + request.getTextContent() + "</p>"));
            }

            // 添加附件
            if (request.getAttachments() != null) {
                for (AttachmentData att : request.getAttachments()) {
                    Attachments attachment = new Attachments();
                    attachment.setFilename(att.getFilename());
                    attachment.setType(att.getType());
                    attachment.setContent(att.getBase64Content());
                    mail.addAttachments(attachment);
                }
            }

            // 添加 CC/BCC（复用上面的 personalization）
            if (request.getCc() != null) {
                for (String cc : request.getCc()) {
                    personalization.addCc(new Email(cc));
                }
            }
            if (request.getBcc() != null) {
                for (String bcc : request.getBcc()) {
                    personalization.addBcc(new Email(bcc));
                }
            }

            Request sendRequest = new Request();
            sendRequest.setMethod(Method.POST);
            sendRequest.setEndpoint("mail/send");
            sendRequest.setBody(mail.build());

            Response response = sendGrid.api(sendRequest);

            log.info("✅ Email sent. Status: {}", response.getStatusCode());

            return ResponseEntity.ok(Map.of(
                "success", response.getStatusCode() >= 200 && response.getStatusCode() < 300,
                "statusCode", response.getStatusCode(),
                "to", request.getTo(),
                "subject", request.getSubject()
            ));

        } catch (IOException e) {
            log.error("❌ Email failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 使用动态模板发送邮件
     */
    @PostMapping("/send-template")
    public ResponseEntity<Map<String, Object>> sendTemplateEmail(@RequestBody TemplateEmailRequest request) {
        try {
            log.info("📧 Sending template email to: {}", request.getTo());

            Mail mail = new Mail();
            mail.setFrom(new Email(fromEmail, fromName));
            mail.setTemplateId(request.getTemplateId());

            Personalization personalization = new Personalization();
            personalization.addTo(new Email(request.getTo(), request.getToName()));
            
            // 动态模板变量
            if (request.getDynamicData() != null) {
                request.getDynamicData().forEach(personalization::addDynamicTemplateData);
            }
            mail.addPersonalization(personalization);

            Request sendRequest = new Request();
            sendRequest.setMethod(Method.POST);
            sendRequest.setEndpoint("mail/send");
            sendRequest.setBody(mail.build());

            Response response = sendGrid.api(sendRequest);

            return ResponseEntity.ok(Map.of(
                "success", response.getStatusCode() >= 200 && response.getStatusCode() < 300,
                "statusCode", response.getStatusCode(),
                "templateId", request.getTemplateId()
            ));

        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 业务场景邮件 ====================

    /**
     * 预约确认邮件
     */
    @PostMapping("/booking-confirmation")
    public ResponseEntity<Map<String, Object>> sendBookingConfirmation(@RequestBody BookingEmailRequest request) {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .booking-details { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; }
                    .detail-row { display: flex; padding: 10px 0; border-bottom: 1px solid #eee; }
                    .detail-label { font-weight: bold; width: 140px; color: #666; }
                    .btn { display: inline-block; background: #667eea; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; margin: 10px 5px; }
                    .btn-cancel { background: #e74c3c; }
                    .footer { text-align: center; color: #999; font-size: 12px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Booking Confirmed!</h1>
                        <p>Thank you for choosing TeaTime Spa</p>
                    </div>
                    <div class="content">
                        <p>Hi <strong>%s</strong>,</p>
                        <p>Your appointment has been successfully booked. Here are your booking details:</p>
                        
                        <div class="booking-details">
                            <div class="detail-row">
                                <span class="detail-label">📋 Booking ID:</span>
                                <span>%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="detail-label">💆 Service:</span>
                                <span>%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="detail-label">👤 Therapist:</span>
                                <span>%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="detail-label">📅 Date:</span>
                                <span>%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="detail-label">⏰ Time:</span>
                                <span>%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="detail-label">⏱️ Duration:</span>
                                <span>%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="detail-label">💰 Total:</span>
                                <span>$%s</span>
                            </div>
                        </div>
                        
                        <p style="text-align: center;">
                            <a href="%s" class="btn">📍 View Location</a>
                            <a href="%s" class="btn btn-cancel">❌ Cancel Booking</a>
                        </p>
                        
                        <p><strong>📍 Location:</strong><br>%s</p>
                        
                        <p style="color: #666; font-size: 14px;">
                            Please arrive 10 minutes before your appointment. If you need to reschedule or cancel, 
                            please do so at least 24 hours in advance.
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2025 TeaTime Spa. All rights reserved.</p>
                        <p>Questions? Reply to this email or call us at (555) 123-4567</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                request.getCustomerName(),
                request.getBookingId(),
                request.getServiceName(),
                request.getStaffName(),
                request.getDate(),
                request.getTime(),
                request.getDuration(),
                request.getPrice(),
                request.getLocationUrl() != null ? request.getLocationUrl() : "#",
                request.getCancelUrl() != null ? request.getCancelUrl() : "#",
                request.getAddress()
            );

        EmailRequest emailRequest = new EmailRequest();
        emailRequest.setTo(request.getCustomerEmail());
        emailRequest.setToName(request.getCustomerName());
        emailRequest.setSubject("✅ Booking Confirmed - " + request.getServiceName());
        emailRequest.setHtmlContent(html);

        return sendEmail(emailRequest);
    }

    /**
     * 预约提醒邮件
     */
    @PostMapping("/booking-reminder")
    public ResponseEntity<Map<String, Object>> sendBookingReminder(@RequestBody BookingEmailRequest request) {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #f39c12; color: white; padding: 20px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #fff9e6; padding: 30px; border-radius: 0 0 10px 10px; }
                    .highlight { background: white; padding: 20px; border-radius: 8px; border-left: 4px solid #f39c12; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>⏰ Appointment Reminder</h1>
                    </div>
                    <div class="content">
                        <p>Hi <strong>%s</strong>,</p>
                        <p>This is a friendly reminder about your upcoming appointment:</p>
                        
                        <div class="highlight">
                            <h3>%s</h3>
                            <p>📅 <strong>%s</strong> at <strong>%s</strong></p>
                            <p>👤 with <strong>%s</strong></p>
                            <p>📍 %s</p>
                        </div>
                        
                        <p>Please remember to arrive 10 minutes early. We look forward to seeing you!</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                request.getCustomerName(),
                request.getServiceName(),
                request.getDate(),
                request.getTime(),
                request.getStaffName(),
                request.getAddress()
            );

        EmailRequest emailRequest = new EmailRequest();
        emailRequest.setTo(request.getCustomerEmail());
        emailRequest.setToName(request.getCustomerName());
        emailRequest.setSubject("⏰ Reminder: " + request.getServiceName() + " Tomorrow");
        emailRequest.setHtmlContent(html);

        return sendEmail(emailRequest);
    }

    /**
     * 支付收据邮件
     */
    @PostMapping("/payment-receipt")
    public ResponseEntity<Map<String, Object>> sendPaymentReceipt(@RequestBody PaymentReceiptRequest request) {
        StringBuilder itemsHtml = new StringBuilder();
        for (LineItem item : request.getItems()) {
            itemsHtml.append(String.format("""
                <tr>
                    <td style="padding: 12px; border-bottom: 1px solid #eee;">%s</td>
                    <td style="padding: 12px; border-bottom: 1px solid #eee; text-align: right;">$%.2f</td>
                </tr>
                """, item.getName(), item.getAmount()));
        }

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #27ae60; color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    table { width: 100%%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; }
                    .total-row { font-weight: bold; font-size: 18px; background: #f0f0f0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>💳 Payment Receipt</h1>
                        <p>Transaction ID: %s</p>
                    </div>
                    <div class="content">
                        <p>Hi <strong>%s</strong>,</p>
                        <p>Thank you for your payment! Here's your receipt:</p>
                        
                        <table>
                            <thead>
                                <tr style="background: #667eea; color: white;">
                                    <th style="padding: 12px; text-align: left;">Description</th>
                                    <th style="padding: 12px; text-align: right;">Amount</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                                <tr class="total-row">
                                    <td style="padding: 12px;">Total</td>
                                    <td style="padding: 12px; text-align: right;">$%.2f</td>
                                </tr>
                            </tbody>
                        </table>
                        
                        <p style="margin-top: 20px; color: #666;">
                            <strong>Payment Method:</strong> %s<br>
                            <strong>Date:</strong> %s
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                request.getTransactionId(),
                request.getCustomerName(),
                itemsHtml.toString(),
                request.getTotal(),
                request.getPaymentMethod(),
                request.getDate()
            );

        EmailRequest emailRequest = new EmailRequest();
        emailRequest.setTo(request.getCustomerEmail());
        emailRequest.setToName(request.getCustomerName());
        emailRequest.setSubject("💳 Payment Receipt - $" + String.format("%.2f", request.getTotal()));
        emailRequest.setHtmlContent(html);

        return sendEmail(emailRequest);
    }

    // ==================== 批量发送 ====================

    /**
     * 批量发送邮件（营销/通知）
     */
    @PostMapping("/send-bulk")
    public ResponseEntity<Map<String, Object>> sendBulkEmail(@RequestBody BulkEmailRequest request) {
        try {
            log.info("📧 Sending bulk email to {} recipients", request.getRecipients().size());

            Mail mail = new Mail();
            mail.setFrom(new Email(fromEmail, fromName));
            mail.setSubject(request.getSubject());
            mail.addContent(new Content("text/html", request.getHtmlContent()));

            // 每个收件人单独的 Personalization（隐藏其他收件人）
            for (Recipient recipient : request.getRecipients()) {
                Personalization personalization = new Personalization();
                personalization.addTo(new Email(recipient.getEmail(), recipient.getName()));
                
                // 个性化变量替换
                personalization.addDynamicTemplateData("name", recipient.getName());
                if (recipient.getCustomData() != null) {
                    recipient.getCustomData().forEach(personalization::addDynamicTemplateData);
                }
                
                mail.addPersonalization(personalization);
            }

            // 添加退订链接
            mail.setTrackingSettings(new TrackingSettings());

            Request sendRequest = new Request();
            sendRequest.setMethod(Method.POST);
            sendRequest.setEndpoint("mail/send");
            sendRequest.setBody(mail.build());

            Response response = sendGrid.api(sendRequest);

            return ResponseEntity.ok(Map.of(
                "success", response.getStatusCode() >= 200 && response.getStatusCode() < 300,
                "statusCode", response.getStatusCode(),
                "recipientCount", request.getRecipients().size()
            ));

        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== DTOs ====================

    @Data
    public static class EmailRequest {
        private String to;
        private String toName;
        private String subject;
        private String textContent;
        private String htmlContent;
        private List<String> cc;
        private List<String> bcc;
        private List<AttachmentData> attachments;
    }

    @Data
    public static class AttachmentData {
        private String filename;
        private String type;  // e.g., "application/pdf"
        private String base64Content;
    }

    @Data
    public static class TemplateEmailRequest {
        private String to;
        private String toName;
        private String templateId;  // SendGrid 动态模板 ID
        private Map<String, Object> dynamicData;
    }

    @Data
    public static class BookingEmailRequest {
        private String customerEmail;
        private String customerName;
        private String bookingId;
        private String serviceName;
        private String staffName;
        private String date;
        private String time;
        private String duration;
        private String price;
        private String address;
        private String locationUrl;
        private String cancelUrl;
    }

    @Data
    public static class PaymentReceiptRequest {
        private String customerEmail;
        private String customerName;
        private String transactionId;
        private List<LineItem> items;
        private Double total;
        private String paymentMethod;
        private String date;
    }

    @Data
    public static class LineItem {
        private String name;
        private Double amount;
    }

    @Data
    public static class BulkEmailRequest {
        private String subject;
        private String htmlContent;
        private List<Recipient> recipients;
    }

    @Data
    public static class Recipient {
        private String email;
        private String name;
        private Map<String, Object> customData;
    }
}