package leonardokruger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PrincipalServer {

    public static final int PORT = 4600;

    public static final String ADDRESS = "127.0.0.2";

    private final Selector selector;

    private final ServerSocketChannel serverChannel;

    private List<SocketChannel> conectedClients;

    private final ByteBuffer buffer;

    public static void main(String[] args) {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "10");

        try {
            PrincipalServer server = new PrincipalServer();
            server.startProcess();
        } catch (IOException e) { System.err.println("Não foi possível iniciar o servidor"); }
    }

    public PrincipalServer() throws IOException {
        buffer = ByteBuffer.allocate(1024);
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        conectedClients = new ArrayList<>();

        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        serverChannel.bind(new InetSocketAddress(ADDRESS, PORT), 10000);
        System.out.println("Servidor iniciado no IP:" + ADDRESS + " na PORTA:" + PORT);
    }

    public void startProcess() {
        while (true)
            try {
                selector.select();
                startProcessingKeys(selector.selectedKeys());
            } catch (IOException e){ System.err.println(e.getMessage()); }
    }

    private void startProcessingKeys(Set<SelectionKey> selectionKeys) {
        selectionKeys.stream().parallel().forEach(selectionKey -> {
            try {
                startProcessingKey(selectionKey);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        selectionKeys.clear();
    }

    private void startProcessingKey(SelectionKey selectionKey) throws InterruptedException {
        if (!selectionKey.isValid()) return;

        try {
            connectionValidation(selectionKey, selector);
            processClientCommand(selectionKey);
        } catch(IOException ex){ System.out.println("Erro ao processar evento: " + ex.getMessage()); }
    }

    private void connectionValidation(SelectionKey key, Selector selector) throws IOException {
        if (!key.isAcceptable()) return;

        SocketChannel clientChannel = serverChannel.accept();
        System.out.println("Cliente " + clientChannel.getRemoteAddress() + " conectado.\n");
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        conectedClients.add(clientChannel);
    }

    private void processClientCommand(SelectionKey selectionKey) throws IOException, InterruptedException {
        if (!selectionKey.isReadable()) return;
        SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
        buffer.clear();
        int bytesRead;

        try {
            bytesRead = clientChannel.read(buffer);
        } catch (IOException e) {
            System.err.println("Erro na leitura de dados");
            clientChannel.close();
            selectionKey.cancel();
            return;
        }

        if (bytesRead <= 0) return;
        buffer.flip();
        byte[] data = new byte[bytesRead];
        buffer.get(data);

        String clientCommandResponse = executeCommandLine(data);
        sendResponseToClient(clientCommandResponse);
    }

    private void sendResponseToClient(String clientCommandResponse){
        conectedClients.forEach(client -> {
            try {
                client.write(ByteBuffer.wrap(clientCommandResponse.getBytes()));
                System.out.println("Response encaminhado ao client: " + client.getRemoteAddress());
            } catch (IOException e) { System.out.println("Não foi possível retornar o responde ao client"); }
        });
    }

    private String executeCommandLine(byte[] data) throws IOException, InterruptedException {
        Process proc = Runtime.getRuntime().exec(new String(data));
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        String line = "";
        String finalStructure = "";
        while((line = reader.readLine()) != null) {
            finalStructure = finalStructure.concat(line).concat("\n");
        }
        proc.waitFor();

        return finalStructure;
    }
}