package com.guild.schedule;

import io.javalin.Javalin;

public class Main {
    public static void main(String[] args) {
        Javalin app = Javalin.create().start(8080);
        System.out.println("Server is running! Waiting for Meta verification...");

        // 1. META SECURITY ROUTE (GET)
        // Meta will hit this URL once to verify you own the server.
        app.get("/whatsapp/webhook", ctx -> {

            // This is our secret password. We will type this into the Meta dashboard later.
            String MY_VERIFY_TOKEN = "hackathon2026";

            String mode = ctx.queryParam("hub.mode");
            String token = ctx.queryParam("hub.verify_token");
            String challenge = ctx.queryParam("hub.challenge");

            if (mode != null && token != null) {
                if (mode.equals("subscribe") && token.equals(MY_VERIFY_TOKEN)) {
                    System.out.println("SUCCESS: Webhook verified by Meta!");
                    // Meta requires us to echo back the challenge string exactly as received
                    ctx.status(200).result(challenge);
                } else {
                    ctx.status(403);
                }
            }
        });

        // 2. META MESSAGE ROUTE (POST)
        // Once verified, Meta will send the actual WhatsApp messages here as JSON.
        app.post("/whatsapp/webhook", ctx -> {
            String rawJson = ctx.body();
            System.out.println("\n--- Incoming Meta Message ---");
            System.out.println(rawJson);

            // You MUST return a 200 OK immediately, or Meta will think your server crashed and spam you.
            ctx.status(200);
        });
    }
}