package leonardokruger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class Client1 implements Runnable {

    private final Scanner scanner;

    private final Selector selector;

    private final SocketChannel clientChannel;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

    public static void main(String[] args) {
        try {
            Client1 client1 = new Client1();
            client1.start();
        } catch (IOException e) { System.out.println("Não foi possível iniciar o client"); }
    }

    public Client1() throws IOException {
        selector = Selector.open();
        clientChannel = SocketChannel.open();
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
        System.out.print("\n --- Liberado para inserção de novo comando ou 'desconectar' --- \n");
        do {
            msg = scanner.nextLine();
            clientChannel.write(ByteBuffer.wrap(msg.getBytes()));
        } while(!msg.equalsIgnoreCase("desconectar"));
    }

    private void processRead() throws IOException {
        buffer.clear();
        int bytesRead = clientChannel.read(buffer);
        buffer.flip();

        if (bytesRead > 0) {
            byte data[] = new byte[bytesRead];
            buffer.get(data);
            System.out.println("\nMensagem recebida do servidor: " + new String(data));
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