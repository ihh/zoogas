import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import java.util.ArrayList;

public abstract class NetworkThread extends Thread {
    /*
    public NetworkThread(ThreadGroup threadGroup, Runnable runnable, String string, long l) {
        super(threadGroup, runnable, string, l);
    }
    public NetworkThread(ThreadGroup threadGroup, Runnable runnable, String string) {
        super(threadGroup, runnable, string);
    }
    public NetworkThread(Runnable runnable, String string) {
        super(runnable, string);
    }
    public NetworkThread(ThreadGroup threadGroup, String string) {
        super(threadGroup, string);
    }
    public NetworkThread(String string) {
        super(string);
    }
    public NetworkThread(ThreadGroup threadGroup, Runnable runnable) {
        super(threadGroup, runnable);
    }
    public NetworkThread(Runnable runnable) {
        super(runnable);
    }
    public NetworkThread() {
        super();
    }
    */

    // constants
    public final int allocateBufferSize = Integer.MAX_VALUE >> 16;
    public final static int CONNECTIONS_FULL = -1;

    // Commands
    public static enum packetCommand {
        PING              (0),
        SEND_SIZE         (2, "ii", 4 + 4),
        CLAIM_GRID        (2, "ii", 4 + 4),
        LAUNCH            (0),
        OBSERVE           (2, "ii", 4 + 4),
        DISCONNECT        (0),

        //CURRENT_CLIENTS  (2, "i(ii)*", 0); // ideally, should be something regex-like
        CURRENT_CLIENTS   (1, "i", 4 + 4 + 4), // variadic
        CONNECT_PEER      (3, "sii", 4), // address, port, int

        CHECKIN_ALL_RULES (1, "i", 4), // variadic
        CHECKIN_RULESET   (1, "i", 4), // variadic
        REQUEST_PARTICLES (0),
        SEND_PARTICLES    (1, "i", 4), // variadic
        
        REFRESH_OBSERVED  (2, "ii", 4 + 4),
        UPDATE_OBSERVED   (2, "ii", 4 + 4); // variadic
        

        private packetCommand(int numArgs) {
            expectedCount = numArgs;
        }
        private packetCommand(int numArgs, String str, int bytes) {
            expectedCount = numArgs;
            expectedArgs = str;
            expectedBytes = bytes;
        }
        private int expectedCount = 0;
        private int expectedBytes = 0; // expected number of bytes, not including the enum itself
        private String expectedArgs = "";
        public int getExpectedCount() {
            return expectedCount;
        }
        public int getNumBytes() {
            return expectedBytes;
        }
        public String getExpectedArgs() {
            return expectedArgs;
        }
        public boolean matchArgCount(int numArgs) {
            return numArgs == expectedCount;
        }
    }
    
    protected ArrayList<Object> collectParameters(packetCommand command, ByteBuffer bb) {
        ArrayList<Object> parameters = new ArrayList<Object>();
        for(int i = 0; i < command.getExpectedCount(); ++i) {
            char c = command.getExpectedArgs().charAt(i);
            switch(c) {
                case 'b':
                    parameters.add(bb.get());
                    break;
                case 'c':
                    parameters.add(bb.getChar());
                    break;
                case 'i':
                    parameters.add(bb.getInt());
                    break;
                case 's':
                    parameters.add(getStringFromBuffer(bb));
                    break;
                default:
                    System.err.println("Unknown parameter type " + c);
                    break;
            }
        }
        
        return parameters;
    }

    abstract void processPacket(ByteBuffer bb);

    // Commonly used helper methods
    public String getStringFromBuffer(ByteBuffer bb) {
        StringBuilder sb = new StringBuilder();
        byte c = bb.get();
        while(c != '\0') {
            sb.append((char)c); // TODO: check non-utf-8
            c = bb.get();
        }
        return sb.toString();
    }
    public ByteBuffer writeStringToBuffer(ByteBuffer bb, String s) {
        s += '\0';
        byte[] stringBytes = s.getBytes();
        bb.put(stringBytes);
        return bb;
    }

    /**
     *Prepares a buffer for <i>sending</i> a packet
     * @param cmd
     * @return
     */
    public ByteBuffer prepareBuffer(packetCommand cmd){
        return prepareBuffer(cmd, cmd.expectedBytes);
    }
    public ByteBuffer prepareBuffer(packetCommand cmd, int byteCount){
        ByteBuffer bb = ByteBuffer.allocate(4 + byteCount);
        bb.putInt(cmd.ordinal());
        return bb;
    }
    public boolean verifyAndSend(ByteBuffer bb, packetCommand cmd, SocketChannel sc, boolean requiresBlocking) {
        bb.flip();
        try {
            if(requiresBlocking)
                sc.configureBlocking(true);
            sc.write(bb);
            if(requiresBlocking)
                sc.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(this.getClass() + ": connection closed already?");
            return false;
        } catch(NullPointerException e) {
            if(sc == null)
                System.err.println("SocketChannel not yet created.");
        }
        return true;
    }
    public boolean verifyAndSend(ByteBuffer bb, packetCommand cmd, SocketChannel sc) {
        //System.out.println(" Sent " + cmd + " " + bb);
        return verifyAndSend(bb, cmd, sc, false);
    }
}
