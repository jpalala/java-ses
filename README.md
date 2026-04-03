# java-ses

Tiny AWS Lambda (Java 17) that sends e-mail via Amazon SES.

Configuration is done with Lambda environment variables; the same function can be triggered from the AWS console, CLI, SDK, or an API Gateway proxy.

---

## 1. Build
```bash
git clone <repo>
cd send-email-lambda
mvn clean package
```
Artifact: `target/send-email-lambda-1.0.0-all.jar`

---

## 2. Create IAM role (once)
```bash
# trust policy – allow Lambda to assume the role
cat > trust.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "lambda.amazonaws.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
EOF
aws iam create-role \
  --role-name lambda-ses-send-email \
  --assume-role-policy-document file://trust.json

# give Lambda basic logging rights
aws iam attach-role-policy \
  --role-name lambda-ses-send-email \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

# allow SES (least privilege – change to your verified address)
cat > ses-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["ses:SendEmail", "ses:SendRawEmail"],
    "Resource": "*",
    "Condition": {
      "StringEquals": {"ses:FromAddress": "verified@example.com"}
    }
  }]
}
EOF
aws iam put-role-policy \
  --role-name lambda-ses-send-email \
  --policy-name SES-Send-Policy \
  --policy-document file://ses-policy.json
```

---

## 3. Deploy the function
```bash
aws lambda create-function \
  --function-name send-email-java \
  --runtime java17 \
  --role arn:aws:iam::<ACCOUNT_ID>:role/lambda-ses-send-email \
  --handler example.SendEmailHandler::handleRequest \
  --zip-file fileb://target/send-email-lambda-1.0.0-all.jar \
  --timeout 30 \
  --memory-size 512 \
  --environment "Variables={\
FROM_ADDRESS=verified@example.com,\
TO_ADDRESS=destination@example.com,\
AWS_REGION=us-east-1}"
```

Update variables later without redeploying code:
```bash
aws lambda update-function-configuration \
  --function-name send-email-java \
  --environment "Variables={\
FROM_ADDRESS=new@example.com,\
TO_ADDRESS=other@example.com,\
AWS_REGION=us-east-1}"
```

---

## 4. Invoke
### A. AWS CLI (synchronous)
```bash
aws lambda invoke \
  --function-name send-email-java \
  --payload '{
    "subject": "CLI test",
    "bodyText": "Sent from AWS CLI",
    "bodyHtml": "<h1>CLI</h1><p>Sent from AWS CLI</p>"
  }' \
  --cli-binary-format raw-in-base64-out \
  response.json
cat response.json
# -> Email sent. MessageId=0100017f...
```

### B. AWS CLI (asynchronous – fire-and-forget)
Add `--invocation-type Event`.

### C. AWS SDK (Java example)
```java
LambdaClient lambda = LambdaClient.create();
String payload = """
    {
      "subject": "SDK test",
      "bodyText": "Plain body",
      "bodyHtml": "<h1>HTML body</h1>"
    }""";
InvokeRequest req = InvokeRequest.builder()
        .functionName("send-email-java")
        .payload(SdkBytes.fromUtf8String(payload))
        .invocationType(InvocationType.REQUEST_RESPONSE)
        .build();
InvokeResponse resp = lambda.invoke(req);
System.out.println(resp.payload().asUtf8String());
```

### D. API Gateway (optional)
Create a new HTTP or REST API, add a `POST /send` method, integration type “Lambda”, and map the incoming JSON body straight through.  
No extra code changes—Lambda already accepts the same JSON shown above.

---

## 5. Environment variables reference
| Name            | Purpose                              | Example                     |
|-----------------|--------------------------------------|-----------------------------|
| `FROM_ADDRESS`  | SES-verified sender                  | `noreply@example.com`       |
| `TO_ADDRESS`    | Default recipient (can be overridden in payload) | `admin@example.com` |
| `AWS_REGION`    | Region for SES endpoint              | `us-east-1`                 |

All three are optional at **build** time; if you omit them you must supply them in the Lambda console or `update-function-configuration`.

---

## 6. Local testing (optional)
```bash
# unit test
mvn test

# local invoke with AWS SAM
sam local invoke -e event.json
# event.json contains the same JSON payload used in the CLI example
```

---

## 7. Clean up
```bash
# 1. Delete the function first
aws lambda delete-function --function-name send-email-java

# 2. Delete the inline policy
aws iam delete-role-policy --role-name lambda-ses-send-email --policy-name SES-Send-Policy

# 3. Detach the managed execution policy
aws iam detach-role-policy --role-name lambda-ses-send-email --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

# 4. Finally, delete the role
aws iam delete-role --role-name lambda-ses-send-email
```

---

## Notes

### API Gateway Integration (Important)
By default, this Lambda expects a clean, direct JSON payload containing the specific email keys. 
* **If you use a REST API with a Custom (Non-Proxy) Integration:** You must use a "Body Mapping Template" to pass the raw payload through. Read more about setting up payload transformations in the [AWS Mapping Templates Guide](https://docs.aws.amazon.com/apigateway/latest/developerguide/models-mappings.html).
* **If you use an HTTP API or a Lambda Proxy Integration:** API Gateway will wrap your payload in a big metadata envelope before handing it to Lambda. To use this without mapping templates, you will need to update your Java code to receive an AWS Proxy Event and manually parse the body.

### IAM Role Cleanup
When tearing down resources via the AWS CLI, programmatic rules apply. AWS will not let you delete an IAM role if it still has active dependencies. You must strip all its policies before you delete the role itself.
1. Use `delete-role-policy` to remove inline policies.
2. Use `detach-role-policy` to remove managed AWS policies.
3. Finally, execute the `delete-role` command.

---

## Troubleshooting
* Make sure to read the above notes on IAM Role cleanup, as well as API Gateway Integration.
* SES must be out of the sandbox in new accounts **or** the destination address verified.  
* If you get “Access denied” from SES, double-check the IAM policy’s `FromAddress` condition matches `FROM_ADDRESS`.  
* The jar must be < 250 MB unzipped and < 50 MB zipped for direct upload; this build is ~12 MB.  
* For heavier dependencies switch to container image or Lambda Layers.

