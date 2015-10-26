package me.osm.gazetteer.web.executions;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.api.meta.health.BackgroundExecution;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.util.LRUMap;

public class BackgroundExecutorFacade {
	
	public static final int FUTURE_TASKS_QUEUE_SIZE = GazetteerWeb.config().getExecutionQueueSize();
	private static final int EXECUTION_HISTORY_SIZE = 100;

	public static abstract class BackgroundExecutableTask implements Runnable {
		
		private final int id = taskCounter.getAndIncrement();
		private volatile boolean runed = false;
		private volatile boolean aborted = false;
		
		public int getId() {
			return id;
		}
		
		protected boolean isAborted() {
			return aborted;
		}

		protected boolean isRuned() {
			return runed;
		}
		
		public void abort() {
			aborted = true;
		}

		public String getCallbackURL() {
			return null;
		}

		@Override
		public final void run() {
			runed = true;
			INSTANCE.activeTasks.add(this.id);
			try{
				executeTask();
				INSTANCE.doneTasks.add(this.id);
				log.info("Task {} is done", this.id);
				
				if(StringUtils.isNotBlank(getCallbackURL())) {
					callBack(getCallbackURL());
				}
			}
			catch (AbortedException abortedE) {
				synchronized (INSTANCE) {
					INSTANCE.abortedTasks.put(this.id, abortedE.isByUser() ? "Aborted by user" : abortedE.getMessage());
				}
				
				if(!abortedE.isByUser()) {
					abortedE.printStackTrace();
					throw new RuntimeException(abortedE.getCause());
				}
			}
			finally {
				INSTANCE.queuedTasks.remove((Object)this.id);
				INSTANCE.activeTasks.remove(this.id);
				
				INSTANCE.cleanupHistory();
			}
		}
		
		private void callBack(String callbackURL) {
			try {
				URLConnection connection = new URL(callbackURL).openConnection();
				InputStream is = connection.getInputStream();
				is.close();
				log.info("Call {}", callbackURL);
			}
			catch (Exception e) {
				log.error("Callback {} invocation failed.", callbackURL, e);
			}
		}

		public boolean submit() {
			synchronized (INSTANCE) {
				if(INSTANCE.queuedTasks.size() > FUTURE_TASKS_QUEUE_SIZE) {
					return false;
				} 
				INSTANCE.queuedTasks.add(this.id);
				INSTANCE.descriptions.put(this.id, this.description());
			}
			
			executor.submit(this);
			
			return true;
			
		}
		
		public abstract void executeTask() throws AbortedException;
		public abstract BackgroudTaskDescription description();
	}

	private static final AtomicInteger taskCounter = new AtomicInteger();
	
	private static ExecutorService executor = Executors.newSingleThreadExecutor();
	
	private final Set<Integer> doneTasks = Collections.synchronizedSet(new LinkedHashSet<Integer>(EXECUTION_HISTORY_SIZE + 2));
	private final Set<Integer> activeTasks = Collections.synchronizedSet(new LinkedHashSet<Integer>(10));
	private final LinkedHashMap<Integer, String> abortedTasks = new LinkedHashMap<Integer, String>(EXECUTION_HISTORY_SIZE + 2);
	private final List<Integer> queuedTasks = new ArrayList<Integer>();
	private final Map<Integer, BackgroudTaskDescription> descriptions 
		= new LRUMap<>(EXECUTION_HISTORY_SIZE + 2 + 10, EXECUTION_HISTORY_SIZE + 2 + 10 + 5);
	
	private static final Logger log = LoggerFactory.getLogger(BackgroundExecutorFacade.class);
	
	private BackgroundExecutorFacade() {
	}
	
	public synchronized void cleanupHistory() {
		if(doneTasks.size() > EXECUTION_HISTORY_SIZE) {
			
			int overhead = doneTasks.size() - EXECUTION_HISTORY_SIZE;
			Iterator<Integer> iterator = doneTasks.iterator();
			
			while(overhead-- > 0) {
				log.info("Remove {} task from DONE tasks execution history", iterator.next());
				iterator.remove();
			}
		}

		if(abortedTasks.size() > EXECUTION_HISTORY_SIZE) {
			
			int overhead = abortedTasks.size() - EXECUTION_HISTORY_SIZE;
			Iterator<Entry<Integer, String>> iterator = abortedTasks.entrySet().iterator();
			
			while(overhead-- > 0) {
				Entry<Integer, String> entry = iterator.next();
				log.info("Remove [{}: {}] task from ABORTED tasks execution history", entry.getKey(), entry.getValue());
				iterator.remove();
			}
		}
	}

	private static final BackgroundExecutorFacade INSTANCE = new BackgroundExecutorFacade();
	
	public static BackgroundExecutorFacade get() {
		return INSTANCE;
	}
	
	public BackgroundExecution getStateInfo() {
		
		BackgroundExecution result = new BackgroundExecution();
		
		result.setThreads(1);
		
		synchronized (INSTANCE) {
			
			List<BackgroudTaskDescription> list = new ArrayList<>();
			for(int tid : activeTasks) {
				list.add(INSTANCE.descriptions.get(tid));
			}
			result.setActive(list);

			list = new ArrayList<>();
			for(int tid : doneTasks) {
				list.add(INSTANCE.descriptions.get(tid));
			}
			result.setDone(list);

<<<<<<< Upstream, based on branch 'develop' of git@github.com:kiselev-dv/gazetteer.git
			
=======
>>>>>>> 6ff4d9c descriptions for background executions
			ArrayList<Integer> queued = new ArrayList<Integer>(queuedTasks);
			for(int tid : activeTasks) {
				int indexOf = queued.indexOf(tid);
				if(indexOf >= 0) {
					queued.remove(indexOf); 
				}
			}
			
			list = new ArrayList<>();
			for(int tid : queued) {
				list.add(INSTANCE.descriptions.get(tid));
			}
			result.setQueued(list);
			
			return result;
		}
	}
	
}
