package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.Map;

public class SendEmailHandler implements RequestHandler<Map<String, Object>, String> {

    // Environment variables: FROM_ADDRESS, TO_ADDRESS, AWS_REGION (e.g., "us-east-1")
    private final SesV2Client ses = SesV2Client.builder()
            .region(Region.of(getEnvOrDefault("AWS_REGION", "us-east-1")))
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .build();

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        String from = System.getenv("FROM_ADDRESS");
        String to   = System.getenv("TO_ADDRESS");

        // Allow subject/body overrides from the event (e.g., API Gateway JSON)
        String subject = (String) event.getOrDefault("subject", "Hello from Lambda + SES");
        String bodyText = (String) event.getOrDefault(
                "bodyText",
                "This is a test email sent from an AWS Lambda function using Amazon SES."
        );
        String bodyHtml = (String) event.getOrDefault(
                "bodyHtml",
                "<html><body><h1>Lambda + SES</h1><p>This is a test email.</p></body></html>"
        );

        try {
            SendEmailRequest req = SendEmailRequest.builder()
                    .fromEmailAddress(from)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(subject).build())
                                    .body(Body.builder()
                                            .text(Content.builder().data(bodyText).build())
                                            .html(Content.builder().data(bodyHtml).build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse resp = ses.sendEmail(req);
            return "Email sent. MessageId=" + resp.messageId();

        } catch (SesV2Exception e) {
            context.getLogger().log("SES error: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    private static String getEnvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
        }
}
