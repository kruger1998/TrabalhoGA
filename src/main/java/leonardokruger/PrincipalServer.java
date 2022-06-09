package leonardokruger;

import com.sun.nio.sctp.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.sun.nio.sctp.AssociationChangeNotification.AssocChangeEvent.COMM_UP;

public class PrincipalServer {

    public static final int PORT = 4600;

    public static final String ADDRESS = "127.0.0.9";

    private final Selector selector;

    private final SctpServerChannel sctpServerChannel;

    private final List<SctpChannel> conectedClients;

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
        sctpServerChannel = SctpServerChannel.open();
        sctpServerChannel.configureBlocking(false);
        conectedClients = new ArrayList<>();

        sctpServerChannel.register(selector, SelectionKey.OP_ACCEPT);

        sctpServerChannel.bind(new InetSocketAddress(ADDRESS, PORT), 10000);
        System.out.println("\nServidor iniciado no IP: " + ADDRESS + " na PORTA:" + PORT + "\n");
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

        SctpChannel clientChannel = sctpServerChannel.accept();

        List<SocketAddress> addresse = new ArrayList<>();
        addresse.addAll(clientChannel.getRemoteAddresses());

        System.out.println("Cliente " + addresse.get(0).toString() + " conectado.\n");
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        conectedClients.add(clientChannel);
    }

    private void processClientCommand(SelectionKey selectionKey) throws IOException, InterruptedException {
        if (!selectionKey.isReadable()) return;
        SctpChannel clientChannel = (SctpChannel) selectionKey.channel();
        buffer.clear();
        MessageInfo bytesRead;

        try {
            AssociationHandler assocHandler = new AssociationHandler();
            bytesRead = clientChannel.receive(buffer, System.out, assocHandler);
        } catch (IOException e) {
            System.err.println("Erro na leitura de dados");
            clientChannel.close();
            selectionKey.cancel();
            return;
        }

        if (Objects.isNull(bytesRead)) return;
        buffer.flip();
        Charset charset = Charset.forName("ISO-8859-1");
        CharsetDecoder decoder = charset.newDecoder();

        String clientCommandResponse = executeCommandLine(decoder.decode(buffer).toString());
        sendResponseToClient(clientCommandResponse);
    }

    private void sendResponseToClient(String clientCommandResponse) {
        if (clientCommandResponse.equals("desconectado")) return;
        conectedClients.forEach(client -> {
            try {
                MessageInfo messageInfo = MessageInfo.createOutgoing(null,
                        0);
                client.send(ByteBuffer.wrap(clientCommandResponse.getBytes()), messageInfo);
                List<SocketAddress> addresse = new ArrayList<>();
                addresse.addAll(client.getRemoteAddresses());
                printServerLog(clientCommandResponse, addresse.get(0).toString());
            } catch (IOException e) { System.out.println("\nErro enviando um dos responses"); }
        });
    }

    private void printServerLog(String clientCommandResponse, String clientInfo){
        if (clientCommandResponse.equals("desconectado")) {
            System.out.println("Client " + clientInfo + " desconectado\n");
        } else {
            System.out.println("Response encaminhado ao client: " + clientInfo);
        }
    }

    private String executeCommandLine(String data) throws InterruptedException {
        String command = data;

        if (command.equalsIgnoreCase("desconectar")) {
            System.out.println("Um client desconectou");
            return "desconectado";
        }

        try {
            Process proc = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            String line = "";
            String finalStructure = "";
            while((line = reader.readLine()) != null) {
                finalStructure = finalStructure.concat(line).concat("\n");
            }
            proc.waitFor();

            finalStructure = finalStructure.concat("\n --- Liberado para inserção de novo comando ou 'desconectar' --- \n");

            return finalStructure;

        } catch (IOException ex) {
            return "Nao foi possivel executar o comando -> " + command + "\n" +
                "\n --- Liberado para insercao de novo comando --- \n";
        }
    }

    static class AssociationHandler
            extends AbstractNotificationHandler<PrintStream>
    {
        public HandlerResult handleNotification(AssociationChangeNotification not,
                                                PrintStream stream) {
            if (not.event().equals(COMM_UP)) {
                int outbound = not.association().maxOutboundStreams();
                int inbound = not.association().maxInboundStreams();
            }

            return HandlerResult.CONTINUE;
        }

        public HandlerResult handleNotification(ShutdownNotification not,
                                                PrintStream stream) {
            return HandlerResult.RETURN;
        }
    }

}