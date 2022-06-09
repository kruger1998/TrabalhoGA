package leonardokruger;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Iterator;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

public class Client3 implements Runnable {

    private final Scanner scanner;

    private final Selector selector;

    private final SctpChannel clientChannel;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

    public static void main(String[] args) {
        try {
            Client3 client3 = new Client3();
            client3.start();
        } catch (IOException e) { System.out.println("Não foi possível iniciar o client"); }
    }

    public Client3() throws IOException {
        selector = Selector.open();
        clientChannel = SctpChannel.open();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        clientChannel.connect(new InetSocketAddress(PrincipalServer.ADDRESS, PrincipalServer.PORT));
        scanner = new Scanner(System.in);
    }

    public void start() throws IOException {
        try {
            selector.select(1000);
            serverConnection();

            new Thread(this).start();
            startSendingMessagesFlux();
        }finally{
            clientChannel.close();
            selector.close();
        }
    }

    private void startSendingMessagesFlux() throws IOException {
        String msg;
        System.out.print("\n --- Liberado para insercao de novo comando --- \n");
        do {
            msg = scanner.nextLine();
            MessageInfo messageInfo = MessageInfo.createOutgoing(null,
                    0);
            clientChannel.send(ByteBuffer.wrap(msg.getBytes()), messageInfo);
        } while(!msg.equalsIgnoreCase("desconectar"));
    }

    private void processRead() throws IOException {
        buffer.clear();
        PrincipalServer.AssociationHandler assocHandler = new PrincipalServer.AssociationHandler();
        MessageInfo bytesRead = clientChannel.receive(buffer, System.out, assocHandler);
        buffer.flip();
        Charset charset = Charset.forName("ISO-8859-1");
        CharsetDecoder decoder = charset.newDecoder();

        if (!Objects.isNull(bytesRead) && bytesRead.isComplete()) {
            System.out.println("\nMensagem recebida do servidor: \n" + decoder.decode(buffer));
        }
    }

    private void serverConnection() throws IOException {
        System.out.println("\nCliente conectado ao servidor");
        if(clientChannel.isConnectionPending()) {
            clientChannel.finishConnect();
        }
    }

    @Override
    public void run() {
        try {
            while (selector.select(1000) > 0) {
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    if (selectionKey.isReadable())
                        processRead();
                    iterator.remove();
                }
            }
        }catch(IOException e) { System.err.println("Erro ao ler dados enviados pelo servidor: " + e.getMessage()); }
    }

}
