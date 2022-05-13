package demo.client;
import demo.Msg;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static demo.Constants.*;
import static demo.Msg.*;
import static demo.Msg.Kind.*;

public class DemoClient {

    public static void main(String[] args) throws IOException {
        out.print("Please, input your name: ");
        String name = in.nextLine().trim();
        DemoClient client = new DemoClient(name);
        client.start();
    }

    private final String name;

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private PrintWriter writer;
    private BufferedReader reader;

    private DemoClient(String name) {
        this.name = name;
    }

    private void start() throws IOException {
        socket = new Socket(SERVER, PORT);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        reader = new BufferedReader(new InputStreamReader(inputStream));
        writer = new PrintWriter(outputStream);

        out.println("Client handler started");

        Msg helo = receiveMsg(reader);

        sendMsg(writer, LOGIN, name);

        Msg repLogin = receiveMsg(reader);

        Path filePath = Paths.get("local", "file1.txt");
        long fileSize = Files.size(filePath);
        sendMsg(writer, UPLOAD, "file1.txt", String.valueOf(fileSize));

        Msg ackn = receiveMsg(reader);

        InputStream fileIn = Files.newInputStream(filePath);
        byte[] bytes = new byte[32];
        long written = 0;
        while (written < fileSize) {
            int n = fileIn.read(bytes);
            outputStream.write(bytes, 0, n);
            written += n;
            out.println("  --- Written " + written);
        }

        Msg upDone = receiveMsg(reader);

        socket.close();

    }

 }
