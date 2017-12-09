package com.lobster1234.lambda;


import junit.framework.TestCase;
import org.junit.Test;


public class LambdaProxyTest extends TestCase{

    @Test
    public void testLambda(){
        String response = new LambdaProxy().invokeLambda("internal-api-function", "{\"a\":\"b\"}");
        assertFalse(response.isEmpty());
    }

}



