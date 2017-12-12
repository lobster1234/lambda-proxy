# HTTP Proxy Server for AWS Lambda Invocations

### Purpose

[Lambda Functions](https://aws.amazon.com/lambda/) are a FaaS implementation on Amazon Web Services. Setting them as HTTP/S endpoints over API Gateway can be complicated, and more often than not is an overkill for simple, internal APIs. 
Besides, API Gateway endpoints for Lambda are public, no matter how we slice and dice it. The recently announced VPC Link for API Gateway only allows the endpoints to route to a NLB target, not a Lambda. 

This is a lightweight HTTP/S proxy written in Java, which wraps a lambda invocation in an APIGatewayLambdaProxyRequest, thereby mimicking the API Gateway-Lambda Proxy Integration.

The `/function` endpoint supports `GET`, `PUT`, `POST`, and `DELETE`. Any request sent to this endpoint is wrapped as [APIGatewayProxyRequestEvent](https://github.com/aws/aws-lambda-java-libs/blob/master/aws-lambda-java-events/src/main/java/com/amazonaws/services/lambda/runtime/events/APIGatewayProxyRequestEvent.java).

The Response from the Lambda is wrapped as [APIGatewayProxyResponseEvent](https://github.com/aws/aws-lambda-java-libs/blob/master/aws-lambda-java-events/src/main/java/com/amazonaws/services/lambda/runtime/events/APIGatewayProxyResponseEvent.java).

Here is the [documentation](http://docs.aws.amazon.com/lambda/latest/dg/eventsources.html) and samples of various events (this proxy only supports APIGatewayProxy events).

The request path, headers, HTTP method, querystring, body, etc. become a part of the event request that is sent to the Lambda _as-is_.

An alternative is to call Lambdas from within the code using AWS SDKs. However, having a proxy helps with monitoring (like NewRelic), security via IAM roles, and logging in a central location.  


### API

* `GET /healthcheck` - Used by the load balancer

* `GET /functions` - Get a list of lambda functions

* `GET | PUT | POST | DELETE  /function` - Invoke the Lambda Function named in the header `x-lambda-function-name`. This header can also contain the ARN of the function.



### Deployment

```bash
$ git clone git@github.com:lobster1234/lambda-proxy.git
$ cd lambda-proxy
$ mvn clean install jetty:run
```

This will run the lambda proxy server on jetty, port 8080. 

To deploy in Tomcat, copy `target/lambda-proxy-1.0-SNAPSHOT.war` and copy it to `$TOMCAT_HOME/webapps` as lambda-proxy.war.

```bash
$ curl -i -X POST http://localhost:8080/lambda-proxy/function -H 'x-lambda-function-name:internal-api-function'
  HTTP/1.1 200
  Content-Type: text/html;charset=utf-8
  Transfer-Encoding: chunked
  Date: Fri, 08 Dec 2017 09:22:43 GMT
  Server: Apache Tomcat/8.5.11
  
  {statusCode: 200,headers: {Connection=keep-alive, Content-Length=32, Content-Type=application/json, 
  Date=Fri, 08 Dec 2017 09:22:59 GMT, X-Amz-Executed-Version=$LATEST, x-amzn-Remapped-Content-Length=0, 
  x-amzn-RequestId=61833d87-dbf9-11e7-ae23-4141f65d26c1, 
  X-Amzn-Trace-Id=root=1-5a2a59f2-57637f74631df77949c1e2f0;sampled=0},body: {"message": "Hello from Lambda"}}

```

### IAM Policy

If this proxy will run in an autoscaling group in AWS (recommended), create a role with the below inline policy for the instances - 

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "InvokePermission",
            "Effect": "Allow",
            "Action": [
                "lambda:InvokeFunction",
                "lambda:ListFunctions"
            ],
            "Resource": "*"
        }
    ]
}  
```

If on-prem or on a laptop, attach the above policy to the IAM user whose credentials will sit in `~/.aws/credentials`.


### Typical Deployment in AWS

![AWS Deployment](lambda_proxy.png)

### Errors


* Missing function name

```bash
$ curl -i  http://localhost:8080/lambda-proxy/function                                          
HTTP/1.1 400
Content-Type: text/html;charset=utf-8
Transfer-Encoding: chunked
Date: Sat, 09 Dec 2017 03:53:12 GMT
Connection: close
Server: Apache Tomcat/8.5.11

{statusCode: 400,body: {'Error':'Must provide x-lambda-function-name header'}}
```

* Function Not Found
```bash
$ curl -i -X POST http://localhost:8080/lambda-proxy/function -H 'x-lambda-function-name:getBankTransactions-dev-get-transactions'
HTTP/1.1 404
Content-Type: text/html;charset=utf-8
Transfer-Encoding: chunked
Date: Fri, 08 Dec 2017 10:00:01 GMT
Server: Apache Tomcat/8.5.11

{statusCode: 404,body: Function not found: arn:aws:lambda:us-east-1:************:function:getBankTransactions-dev-get-transactions 
(Service: AWSLambda; Status Code: 404; Error Code: ResourceNotFoundException; Request ID: 980ec390-dbfe-11e7-8fdc-4b6113454448)}

```

* Runtime Failure

```bash
$ curl -i -X POST http://localhost:8080/lambda-proxy/function -H 'x-lambda-function-name:getBankTransactions-dev-get-accounts'
HTTP/1.1 500
Content-Type: text/html;charset=utf-8
Transfer-Encoding: chunked
Date: Fri, 08 Dec 2017 09:56:01 GMT
Connection: close
Server: Apache Tomcat/8.5.11

{statusCode: 500,headers: {Connection=keep-alive, Content-Length=236, Content-Type=application/json, 
Date=Fri, 08 Dec 2017 09:56:17 GMT, X-Amz-Executed-Version=$LATEST, X-Amz-Function-Error=Unhandled, 
x-amzn-Remapped-Content-Length=0, x-amzn-RequestId=08f3a268-dbfe-11e7-8f54-c1cb7d05c976, 
X-Amzn-Trace-Id=root=1-5a2a61c1-652c7848387387a77d5e803f;sampled=0},body: {"errorMessage":
"Could not initialize class com.foo.bar.serverless.YahooConnector",
"errorType":"java.lang.NoClassDefFoundError",
"stackTrace":["com.foo.bar.serverless.AccountsHandler.handleRequest(AccountsHandler.java:34)"]}}
```