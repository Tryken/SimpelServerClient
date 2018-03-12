package de.sciesla.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import de.sciesla.datapackage.DataPackage;
import de.sciesla.datapackage.KickDataPackage;
import de.sciesla.datapackage.MessageDataPackage;
import de.sciesla.event.Event;
import de.sciesla.event.EventHandler;
import de.sciesla.event.EventManager;
import de.sciesla.server.connection.Connection;
import de.sciesla.server.logger.LogType;
import de.sciesla.server.logger.Logger;

public abstract class Server {

	private static Server instance;

	private int port;
	private int maxClients;
	private String password;

	private ServerState serverState;

	private ServerSocket listener;
	private ArrayList<Connection> connections;

	private EventManager eventManager;
	
	private int maxTps = 20;
	private int tps;
	private long tick;
	private float deltaTime;
	private Thread updateThread;

	public abstract void onInit();
	public abstract void onStart();
	public abstract void onStop();
	public abstract void onUpdate(float deltaTime);

	public Server(int port, int maxClients, String password) {

		instance = this;

		this.port = port;
		this.maxClients = maxClients;
		this.password = password;

		serverState = ServerState.STOPPED;

		init();
	}

	private void init() {
		Logger.log(LogType.INFO, "Server Init");

		connections = new ArrayList<>();

		eventManager = new EventManager();
		
		onInit();
	}

	public void start() {
		if (serverState != ServerState.STOPPED) {
			Logger.log(LogType.ERROR, "Server l�uft bereits!");
			return;
		}

		setServerState(ServerState.STARTING);

		setServerState(ServerState.STARTED);
		new Thread() {
			@Override
			public void run() {
				try {
					listener = new ServerSocket(port);
					Logger.log(LogType.INFO, "Listening on Port " + port);

					while (true) {

						Socket socket = listener.accept();
						Connection connection = new Connection(socket, password.equalsIgnoreCase(""));
						connections.add(connection);
						connection.start();

					}
				} catch (IOException e) {

					Logger.log(LogType.ERROR, "Ein Server l�uft bereits unter diesem");
					Server.getInstance().stop();
				} finally {

				}

			}
		}.start();

		onStart();

		updateThread = new Thread() {
			@Override
			public void run() {

				long lastTime = System.nanoTime();

				while (true) {
					onUpdate(deltaTime);

					tick++;
					deltaTime = (int) (System.nanoTime() - lastTime) / 1000000;
					if (deltaTime < (float) (1000 / maxTps)) {
						try {
							Thread.sleep((long) ((float) (1000 / maxTps) - deltaTime));
							deltaTime = (int) (System.nanoTime() - lastTime) / 1000000;
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					tps = (int) (1000 / deltaTime);
					lastTime = System.nanoTime();
				}
			}
		};
		updateThread.start();
	}

	public void stop() {
		if (serverState != ServerState.STARTED && serverState != ServerState.RESTARTING) {
			Logger.log(LogType.ERROR, "Server l�uft nicht!");
			return;
		}

		setServerState(ServerState.STOPPING);

		try {
			listener.close();
			listener = null;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {

		}

		setServerState(ServerState.STOPPED);

		onStop();
	}

	public void restart() {
		if (serverState != ServerState.STARTED && serverState != ServerState.RESTARTING) {
			Logger.log(LogType.ERROR, "Server l�uft nicht!");
			return;
		}

		setServerState(ServerState.RESTARTING);

		init();
		stop();
		start();
	}
	
	public void sendDataPackage(Connection connection, DataPackage datapackage) {
		connection.sendDataPackage(datapackage);
	}

	public void broadcastDataPackage(DataPackage datapackage) {
		for (Connection connection : connections)
			if (connection.isAuthenticated())
				connection.sendDataPackage(datapackage);
	}
	
	public static void brodcastMessage(String message) {
		
		Logger.log(LogType.INFO, message);
		getInstance().broadcastDataPackage(new MessageDataPackage(message));
	}
	
	public static void callEvent(Event event, Runnable runnable) {
		getInstance().getEventManager().callEvent(event, runnable);
	}

	private void setServerState(ServerState state) {
		this.serverState = state;
		Logger.log(LogType.INFO, "Server " + state.getName());
	}

	public void removeConnection(Connection connection) {
		connections.remove(connection);
	}

	public void removeConnection(Connection connection, String reason) {

		Logger.log(LogType.INFO, "Kick " + connection.getUserName() + " reason: " + reason);
		connection.setAuthenticated(false);
		sendDataPackage(connection, new KickDataPackage(reason));
		removeConnection(connection);
	}

	public int getConnectionAmount() {

		int i = 0;

		for (Connection connection : connections)
			if (connection.isAuthenticated())
				i++;

		return i;
	}

	public boolean isConnectionLimitReached() {
		return (getMaxClients() != -1 && getConnectionAmount() > getMaxClients());
	}
	
	public void registerEventHandler(EventHandler eventHandler) {
		getEventManager().registerEventHandler(eventHandler);
	}

	public EventManager getEventManager() {
		return eventManager;
	}
	/**
	 * @return the maxClients
	 */
	public int getMaxClients() {
		return maxClients;
	}

	/**
	 * @param maxClients
	 *            the maxClients to set
	 */
	public void setMaxClients(int maxClients) {
		this.maxClients = maxClients;
	}

	public boolean isPasswordCorrect(String password) {
		return (this.password.equalsIgnoreCase("") || password.equalsIgnoreCase(this.password));
	}

	/**
	 * @param password
	 *            the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @return the maxUps
	 */
	public int getMaxTps() {
		return maxTps;
	}

	/**
	 * @param maxUps
	 *            the maxUps to set
	 */
	public void setMaxTps(int maxTps) {
		this.maxTps = maxTps;
	}

	/**
	 * @return the ups
	 */
	public int getTps() {
		return tps;
	}

	/**
	 * @return the tick
	 */
	public long getTick() {
		return tick;
	}

	/**
	 * @return the deltaTime
	 */
	public float getDeltaTime() {
		return deltaTime;
	}

	public static Server getInstance() {
		return instance;
	}
}
