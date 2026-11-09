import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

public class RemoteMidlet extends MIDlet
        implements CommandListener {

    // Computer Bluetooth address, not the phone address.
    private static final String URL =
        "btspp://381868D35977:3;authenticate=true;encrypt=true";

    private RemoteScreen screen;
    private Command exit;

    private volatile boolean busy = false;
    private volatile boolean closing = false;
    private volatile StreamConnection active;

    public RemoteMidlet() {
        screen = new RemoteScreen();

        exit = new Command("Exit", Command.EXIT, 1);
        screen.addCommand(exit);
        screen.setCommandListener(this);
    }

    protected void startApp() {
        Display.getDisplay(this).setCurrent(screen);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
        closing = true;

        StreamConnection connection = active;

        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void commandAction(Command command, Displayable display) {
        if (command == exit) {
            destroyApp(true);
            notifyDestroyed();
        }
    }

    private void send(final String command) {
        if (busy || closing) {
            return;
        }

        busy = true;
        screen.status = "Sending: " + command;
        screen.repaint();

        new Thread(new Runnable() {
            public void run() {
                transfer(command);
            }
        }).start();
    }

    private void transfer(String command) {
        StreamConnection connection = null;
        OutputStream output = null;
        InputStream input = null;
        String result;

        try {
            connection = (StreamConnection) Connector.open(URL);
            active = connection;

            if (closing) {
                throw new java.io.IOException("Closing");
            }

            output = connection.openOutputStream();
            input = connection.openInputStream();

            output.write((command + "\n").getBytes("UTF-8"));
            output.flush();

            StringBuffer reply = new StringBuffer();
            int value;

            while ((value = input.read()) != -1) {
                if (value == '\n') {
                    break;
                }

                reply.append((char) value);

                if (reply.length() > 32) {
                    throw new java.io.IOException("Invalid reply");
                }
            }

            if ("OK".equals(reply.toString())) {
                result = command + ": OK";
            } else {
                result = "Server error. Check PC.";
            }

        } catch (Exception error) {
            result = error.toString();

        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }

            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }

            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
            }

            active = null;
        }

        final String message = result;

        Display.getDisplay(this).callSerially(new Runnable() {
            public void run() {
                busy = false;

                if (!closing) {
                    screen.status = message;
                    screen.repaint();
                }
            }
        });
    }

    private class RemoteScreen extends Canvas {
        private String status = "Ready";

        protected void paint(Graphics g) {
            int width = getWidth();

            g.setColor(0x15202B);
            g.fillRect(0, 0, width, getHeight());

            g.setColor(0x66DDCC);
            g.drawString(
                "NOKIA REMOTE",
                width / 2, 12,
                Graphics.TOP | Graphics.HCENTER
            );

            g.setColor(0xFFFFFF);

            String[] lines = {
                "5 / Center : Play / Pause",
                "4 / Left   : Previous",
                "6 / Right  : Next",
                "2 / Up     : Volume +",
                "8 / Down   : Volume -",
                "0          : Mute"
            };

            int y = 48;

            for (int i = 0; i < lines.length; i++) {
                g.drawString(
                    lines[i], 8, y,
                    Graphics.TOP | Graphics.LEFT
                );
                y += 23;
            }

            g.setColor(0x66DDCC);
            g.drawString(
                status, 8, y + 12,
                Graphics.TOP | Graphics.LEFT
            );
        }

        protected void keyPressed(int keyCode) {
            // Handle number keys before game actions.
            switch (keyCode) {
                case KEY_NUM5:
                    send("PLAY");
                    return;
                case KEY_NUM4:
                    send("PREV");
                    return;
                case KEY_NUM6:
                    send("NEXT");
                    return;
                case KEY_NUM2:
                    send("VOL_UP");
                    return;
                case KEY_NUM8:
                    send("VOL_DOWN");
                    return;
                case KEY_NUM0:
                    send("MUTE");
                    return;
            }

            int action;

            try {
                action = getGameAction(keyCode);
            } catch (IllegalArgumentException error) {
                return;
            }

            switch (action) {
                case FIRE:
                    send("PLAY");
                    break;
                case LEFT:
                    send("PREV");
                    break;
                case RIGHT:
                    send("NEXT");
                    break;
                case UP:
                    send("VOL_UP");
                    break;
                case DOWN:
                    send("VOL_DOWN");
                    break;
            }
        }
    }
}
