package com.minenash.embedded_assets.server;

import com.google.common.hash.Hashing;
import com.ibm.icu.impl.Pair;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalResourcePackHoster extends Thread {

    private static final Path path = Path.of("resources.zip");
    private static final byte[] EMPTY_PACK = new byte[] {80, 75, 3, 4, 20, 0, 8, 8, 8, 0,  29, 3, 84, 85, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 11, 0, 0, 0, 112, 97, 99, 107, 46, 109, 99, 109, 101, 116, 97, 45, -116, 49, 10, -128,
            48, 12, 69, -9, -98, 34, 116, 82, 16, 28, 69, 103, -17, 33, -47, 68, 40, 106, 91, 76, 54, -15, 60, -34, -61,
            -109, 25, -47, -19, -1, -57, -29, 29, 14, -64, 103, -100, 22, -33, -63, 97, -5, 127, -61, -100, -10, 13, -43,
            96, 91, 125, -108, 88, -90, 61, 100, 13, 41, 26, -11, 5, -95, -30, 107, 74, 9, 125, -126, -5, -30, -102, -73,
            -111, -119, -104, 6, 20, 97, -107, -5, 106, -64, 42, 64, -84, 24, 86, 38, 8, 113, 78, -34, 98, -89, 59, -35,
            3, 80, 75, 7, 8, 80, -96, -22, 43, 102, 0, 0, 0, 119, 0, 0, 0, 80, 75, 3, 4, 20, 0, 8, 8, 8, 0,  29, 3, 84,
            85, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0, 97, 115, 115, 101, 116, 115, 47, 3, 0, 80, 75, 7, 8, 0,
            0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 80, 75, 1, 2, 20, 0, 20, 0, 8, 8, 8, 0, 29, 3, 84, 85, 80, -96, -22, 43, 102,
            0, 0, 0, 119, 0, 0, 0, 11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 112, 97, 99, 107, 46, 109, 99,
            109, 101, 116, 97, 80, 75, 1, 2, 20, 0, 20, 0, 8, 8, 8, 0, 29, 3, 84, 85, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0,
            7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -97, 0, 0, 0, 97, 115, 115, 101, 116, 115, 47, 80, 75, 5, 6, 0, 0,
            0, 0, 2, 0, 2, 0, 110, 0, 0, 0, -42, 0, 0, 0, 0, 0};

    private static final String EMPTY_PACK_SHA1 = Hashing.sha1().hashBytes(EMPTY_PACK).toString();

    public static volatile boolean running = false;
    public static String url = "";
    public static String empty_url = "";
    public static String ip = "";
    private static ServerSocket socket;
    public static String hashCache = "";

    public static void sendPack(ServerPlayNetworkHandler handler) {
        if (EAConfig.localResourcePackHostingConfig.enabled) {
            sendPack(handler.player);
        }
    }
    public static void sendPack(ServerPlayerEntity player) {
        player.sendResourcePackUrl(url + hashCache + ".zip", hashCache, EAConfig.localResourcePackHostingConfig.requireClientToHavePack, EAConfig.getPromptMsg());
    }
    public static void reset(ServerPlayerEntity player) {
        player.sendResourcePackUrl(empty_url, EMPTY_PACK_SHA1, EAConfig.localResourcePackHostingConfig.requireClientToHavePack, Text.literal("This removes the Server Resource Pack"));
    }

    public static boolean startHttpd() {
        if (!EAConfig.localResourcePackHostingConfig.enabled)
            return false;
        try {
            int port = EAConfig.localResourcePackHostingConfig.port;
            if (EAConfig.localResourcePackHostingConfig.local)
                ip = "127.0.0.1";
            else
                try (InputStream stream = new URL( "https://api.ipify.org" ).openStream();
                     Scanner s = new Scanner(stream).useDelimiter( "\\A" )) {
                    ip = s.next();
                }

            url = "http://" + ip + ":" + port + "/";
            empty_url = "http://" + ip + ":" + port + "/empty.zip";
            socket = new ServerSocket(port);
            socket.setReuseAddress(true);
            new LocalResourcePackHoster().start();

            System.out.println( "\u001B[32mSuccessfully started the mini http daemon! Serving only: " + url);
            return true;
        } catch ( IOException e1 ) {
            System.out.println( "\u001B[31mUnable to start the mini http daemon! Disabling..." );
            return false;
        }
    }

    public static void stopHttpd() {
        running = false;
        System.out.println( "\u001B[32mStopped the mini http daemon!");
        if (!socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String calcSHA1() {
        try { return DigestUtils.sha1Hex( Files.readAllBytes(Path.of("resources.zip")) ); }
        catch (Exception e) { e.printStackTrace(); }
        return "";
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                new Thread(new MineConnection(socket.accept())).start();
            } catch (IOException e) {
                System.out.print("A thread was interrupted in a mini http daemon!");
            }
        }
        if (!socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private static Pair<InputStream,Long> verbose( Object object ) {
        if ( EAConfig.localResourcePackHostingConfig.verboseLogging)
            System.out.println( object.toString() );
        return null;
    }

    public Pair<InputStream,Long> requestFileCallback(MineConnection connection, String request, int step) {
        String clientAddrs = connection.client.getInetAddress().getHostAddress();

        String player = null;
        for (var p : EmbeddedAssetsServer.server.getPlayerManager().getPlayerList()) {
            String ip = p.getIp();
            if (ip.equals(clientAddrs) || ip.equals("127.0.0.1") || ip.equals("[0"))
                player = p.getName().getString();
        }

        if ( player == null) {
            if (step >= 3)
                return verbose("Unknown connection from '" + clientAddrs + "'. Aborting...");
            return requestFileCallback(connection, request, step+1);
        }

        if (request.equals("empty.zip")) {
            verbose( "Serving '/empty.zip' to " + player + "(" + clientAddrs + ")" );
            return Pair.of(new ByteArrayInputStream(EMPTY_PACK), (long) EMPTY_PACK.length);
        }

        if (!request.equals(hashCache+".zip"))
            return verbose( player + "(" + clientAddrs + ") requested a file that wasn't '" + hashCache + ".zip' or '/empty.zip', Aborting..." );

        if (!Files.exists(path))
            return verbose("resources.zip file is missing!" );

        try {
            verbose( "Serving 'resources.zip' to " + player + "(" + clientAddrs + ")" );
            return Pair.of(Files.newInputStream(path), Files.size(path));
        }
        catch (IOException e) {
            verbose( "Error serving '" + request + "' to " + player + "(" + clientAddrs + "):" );
            e.printStackTrace();
            return null;
        }

    }

    public class MineConnection implements Runnable {
        public final Socket client;

        public MineConnection(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "8859_1"));
                OutputStream out = client.getOutputStream();
                PrintWriter pout = new PrintWriter(new OutputStreamWriter(out, "8859_1"), true);
                String request = in.readLine();
                verbose( "Request '" + request + "' recieved from " + client.getInetAddress() );

                Matcher get = Pattern.compile("GET /?(\\S*).*").matcher(request);
                if (get.matches()) {
                    request = get.group(1);
                    Pair<InputStream,Long> result = requestFileCallback(this, request, 0);
                    if (result == null) {
                        pout.println("HTTP/1.0 400 Bad Request");
                        onRequestError(400);
                    } else {
                        try {
                            out.write("HTTP/1.0 200 OK\r\n".getBytes());
                            out.write("Content-Type: application/zip\r\n".getBytes());
                            out.write(("Content-Length: " + result.second + "\r\n").getBytes());
                            out.write(("Date: " + DateFormat.getDateInstance().format(new Date()) + "\r\n").getBytes());
                            out.write("Server: MineHttpd\r\n\r\n".getBytes());
                            byte[] data = new byte[64 * 1024];
                            for (int read; (read = result.first.read(data)) > -1; ) {
                                out.write(data, 0, read);
                            }
                            out.flush();
                            result.first.close();
                            verbose( "Successfully served '" + request + "' to " + client.getInetAddress() );
                        } catch (FileNotFoundException e) {
                            pout.println("HTTP/1.0 404 Object Not Found");
                            onRequestError(404);
                        }
                    }
                } else {
                    pout.println("HTTP/1.0 400 Bad Request");
                    onRequestError(400);
                }
                client.close();
            } catch (IOException e) {
                System.out.println("I/O error " + e);
            }
        }

        public void onRequestError(int code) {
            verbose( "Error " + code + " when attempting to serve " + client.getInetAddress() );
        }
    }
}