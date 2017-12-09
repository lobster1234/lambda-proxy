package com.lobster1234.lambda;

import static spark.Spark.*;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.*;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import spark.Request;
import spark.servlet.SparkApplication;


import java.util.Map;
import java.util.stream.Collectors;

public class LambdaProxy implements SparkApplication {

    private final AWSLambda lambdaClient = AWSLambdaAsyncClientBuilder.standard().withRegion(Regions.US_EAST_1).build();

    private final Gson gson = new Gson();

    @Override
    public void init() {

        get("/healthcheck", (req, res) -> "OK");

        get("/function", (req, res) -> {
            APIGatewayProxyResponseEvent response = invokeLambda(req);
            res.status(response.getStatusCode());
            return response;
        });
        put("/function", (req, res) -> {
            APIGatewayProxyResponseEvent response = invokeLambda(req);
            res.status(response.getStatusCode());
            return response;
        });
        delete("/function", (req, res) -> {
            APIGatewayProxyResponseEvent response = invokeLambda(req);
            res.status(response.getStatusCode());
            return response;
        });
        post("/function", (req, res) -> {
            APIGatewayProxyResponseEvent response = invokeLambda(req);
            res.status(response.getStatusCode());
            return response;
        });

    }

    String invokeLambda(String functionName, String payload) {
        InvokeRequest invokeRequest = new InvokeRequest();
        invokeRequest.setInvocationType(InvocationType.RequestResponse);
        invokeRequest.withFunctionName(functionName).withPayload(payload);
        try {
            InvokeResult invokeResponse = lambdaClient.invoke(invokeRequest);
            String response = new String(invokeResponse.getPayload().array(), "UTF-8");
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private APIGatewayProxyResponseEvent invokeLambda(Request req) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        if (req.headers("x-lambda-function-name") == null) {
            response.setStatusCode(400);
            response.setBody("{'Error':'Must provide x-lambda-function-name header'}");
            return response;
        }

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent().withHeaders(getHeaders(req)).withBody(req.body()).
                withQueryStringParamters(flatQueryMap(req)).withHttpMethod(req.requestMethod()).withPath(req.pathInfo());
        InvokeRequest invokeRequest = new InvokeRequest();
        invokeRequest.setInvocationType(InvocationType.RequestResponse);
        invokeRequest.withFunctionName(req.headers("x-lambda-function-name")).withPayload(gson.toJson(event));
        try {
            InvokeResult invokeResponse = lambdaClient.invoke(invokeRequest);
            String payload = new String(invokeResponse.getPayload().array(), "UTF-8");
            response.setStatusCode(invokeResponse.getStatusCode());
            response.setHeaders(invokeResponse.getSdkHttpMetadata().getHttpHeaders());
            response.setBody(payload);
            if (invokeResponse.getFunctionError() != null) {
                System.out.println(invokeResponse.getFunctionError());
                response.setStatusCode(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof ResourceNotFoundException)
                response.setStatusCode(404);
            else response.setStatusCode(500);
            response.setBody(e.getMessage());
        }
        return response;
    }


    private Map<String, String> getHeaders(Request req) {
        return req.headers().stream().collect(Collectors.toMap(x -> x, x -> req.headers(x)));
    }

    private Map<String, String> flatQueryMap(Request req) {
        return req.queryParams().stream().collect(Collectors.toMap(x -> x, x -> req.queryParams(x)));
    }

}