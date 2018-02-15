package com.turnalan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;

class AnalyticsEvent
{
	public String custom_params;
	public String name;
	public long ts;
}

@RestController
@EnableAutoConfiguration
@SpringBootApplication
@EnableAsync
public class StandardEventServerApplication extends SpringBootServletInitializer 
{
	HashMap<String, PriorityQueue<AnalyticsEvent>> hashMap = new HashMap<>();
	HashMap<String, Object> remoteSettings = new HashMap<>(); 
	
	HashMap<String, String> guidStatus = new HashMap<>();
	Queue<String> waitingQueue = new LinkedList<>();

	String curUser = null;
	LocalTime lastUserAcitityTime = null;
	LocalTime LastRequestOnQueueTime = null;
	
	@RequestMapping(value = "/events/debug/showevent", method=RequestMethod.GET)
    String ShowEvents() 
	{
		StringBuilder sb = new StringBuilder();
		
		for (String key : hashMap.keySet()) 
		{	
			sb.append(key);
			sb.append(":" + System.lineSeparator());
			
			for (AnalyticsEvent e : hashMap.get(key))
			{
				sb.append(e.custom_params);
				sb.append("," + System.lineSeparator());
			}
			
			sb.append("; " + System.lineSeparator());
		}
		
		return sb.toString();
    }
	
	@RequestMapping(value = "/events/debug/showguid", method=RequestMethod.GET)
    String ShowGuid() 
	{
		StringBuilder sb = new StringBuilder();
		
		for (String key : guidStatus.keySet()) 
		{	
			sb.append(key);
			sb.append(":" + System.lineSeparator());
			
			sb.append(guidStatus.get(key));
			sb.append(";" + System.lineSeparator());
		}
		
		return sb.toString();
    }
	
	 @RequestMapping(value = "/events/debug/isUsing", method=RequestMethod.GET)
	 boolean isUsing() 
	 {
		 return curUser != null;
	 }
	 
	 @RequestMapping(value = "/events/debug/showQueue", method=RequestMethod.GET)
	 String showQueue() 
	 {
		 StringBuilder sb = new StringBuilder();
		 sb.append("LastRequestOnQueueTime: "+ LastRequestOnQueueTime + ", ");
			
		 for (String key : waitingQueue)
		 {
			 sb.append(key + ", ");
		 }
		 
		 return sb.toString();
	 }
	    
	 @RequestMapping(value = "/events/debug/curUser", method=RequestMethod.GET)
	 String showCurUser() 
	 {
		 if (curUser != null && lastUserAcitityTime != null)
		 {
			 return String.format("%s is currennt using this server, and last active time is %s. It's been %s min since last activity", 
	    			curUser, lastUserAcitityTime, Math.abs(Duration.between(LocalTime.now(), lastUserAcitityTime).toMinutes()));
		 }
    	
		 return "Server is waiting";
	 }
	    
    @RequestMapping(value = "/events/debug/reset", method=RequestMethod.GET)
    String Reset() 
	{
    	hashMap.clear();
    	remoteSettings.clear();
    	guidStatus.clear();
    	waitingQueue.clear();
		curUser = null;
		lastUserAcitityTime = null;
		LastRequestOnQueueTime = null;
		return "done";
    }
    
    // Above are all debug endpoints
    
    @RequestMapping(value = "/events/{name}/{order}", method=RequestMethod.GET)
    String GetEvent(@PathVariable String name, @PathVariable int order) 
    {
    	if (curUser != null)
    	{
    		lastUserAcitityTime = LocalTime.now();
    	}
    	
    	if (order < 0)
    	{
    		return "none";
    	}
    	
    	if (!hashMap.containsKey(name)) 
    	{
    		return "none";
    	}
    	
    	if (order >= hashMap.get(name).size()) 
    	{
    		return "none";
    	}
    	
    	return new ArrayList<AnalyticsEvent>(hashMap.get(name)).get(order).custom_params;
    }
    
    @RequestMapping(value = "/events/guid/{guid}", method=RequestMethod.GET)
    String GetStatus(@PathVariable String guid) 
    {
    	if (!guidStatus.containsKey(guid))
    	{
    		return "NOT_FOUND";
    	}
    	
    	return guidStatus.get(guid).toString();
    }
    
    @RequestMapping(value = "/events/request", method=RequestMethod.POST)
    String RequestServerUsage(@RequestBody String guid) 
    {
    	if (curUser!= null && curUser.equals(guid))
    	{
    		return "ok";
    	}
    	
    	// Add to the waiting queue if it is not
    	if (!waitingQueue.contains(guid))
		{
			waitingQueue.add(guid);
		}
    	
    	// If there is someone who is using the serve, and the not active time is less than 1 min
    	// deny the access request, but add it to the waiting queue
    	if (curUser != null && Math.abs(Duration.between(LocalTime.now(), lastUserAcitityTime).toMinutes()) <= 1)
    	{
    		return "deny";
    	}
    	
    	// If the previous user is not active more than 1 min, 
    	// let the first request on the waiting queue take over the server
    	if (curUser != null && Math.abs(Duration.between(LocalTime.now(), lastUserAcitityTime).toMinutes()) > 1)
    	{
    		guidStatus.put(curUser, "TIMED_OUT");
    		hashMap.clear();
    		remoteSettings.clear();
        	curUser = null;
        	lastUserAcitityTime = null;
    		return "deny";
    	}
    	
    	// If the request is not the first request, and the queue is not empty, deny it
    	if (!guid.equals(waitingQueue.peek()))
    	{
    		if (LastRequestOnQueueTime == null)
    		{
    			LastRequestOnQueueTime = LocalTime.now();
    			return "deny";
    		}
    		
    		if (Math.abs(Duration.between(LocalTime.now(), LastRequestOnQueueTime).toMinutes()) <= 1)
    		{
    			return "deny";
    		}
    		
    		if (Math.abs(Duration.between(LocalTime.now(), LastRequestOnQueueTime).toMinutes()) > 1)
    		{
    			waitingQueue.poll();
    			LastRequestOnQueueTime = null;
    			return "deny";
    		}
    	}
    	
    	// If the request is the first request OR the queue is empty
    	curUser = waitingQueue.poll();
    	hashMap.clear();
    	remoteSettings.clear();
    	lastUserAcitityTime = LocalTime.now();
    	LastRequestOnQueueTime = null;
    	guidStatus.put(curUser, "STARTED");
    	return "ok";
    }
    
    @RequestMapping(value = "/events/finish", method=RequestMethod.POST)
    String FinishServerUsage(@RequestBody String string) 
    {
    	String guid = string.split(":")[0];
    	String test_passed = string.split(":")[1];
    	String test_total = string.split(":")[2];
    	
    	if (curUser == null)
    	{
    		return "curUser is null";
    	}
    	
    	if (!curUser.equals(guid))
    	{
    		return "curUser does not match with the post" + curUser + ":" + string;
    	}
    	
    	hashMap.clear();
    	curUser = null;
    	lastUserAcitityTime = null;
    	guidStatus.put(guid, "COMPLETED:" + test_passed + ":" + test_total);
    	return "ok";
    }
    
    @RequestMapping(value = "/events", method=RequestMethod.POST)
	void PostEvent(@RequestBody String string)  
    {
    	try
    	{
	        JSONObject obj = new JSONObject(string); 
			JSONArray array = obj.getJSONArray("events");
			
			for (int i = 0; i < array.length(); i++) 
			{
				try 
				{
					JSONObject entry = array.getJSONObject(i);
					
					String type = entry.getString("type");
					
					if (type.equals("custom")) 
					{	
						AnalyticsEvent event = new AnalyticsEvent();
						event.name = entry.getString("name");
						event.ts = Long.parseLong(entry.getString("ts"));
						
						System.out.println(event.name);
						System.out.println(event.ts);
						
						if (entry.has("custom_params"))
						{
							event.custom_params = entry.getString("custom_params");
							System.out.println(event.custom_params);
						}

						System.out.println();
						
						if (!hashMap.containsKey(event.name)) 
						{
							hashMap.put(event.name, new PriorityQueue<AnalyticsEvent>(new StringLengthComparator()));
							hashMap.get(event.name).add(event);
							continue;
						}

//						if (hashMap.get(name).contains(custom_params)) 
//						{
//							continue;
//						}
						
						hashMap.get(event.name).add(event);
					}
				
				} catch(Exception e) {
					continue;
				}
			}
    	} catch (Exception e) {
    		
    		try
        	{
    			String[] splitedString = string.split("\n");
    			
    			for (int i = 1; i < splitedString.length; i++) 
    			{
    				try 
    				{
    					String reformated = splitedString[i];
    	    			
    					JSONObject obj = new JSONObject(reformated); 
    					
    					if (obj.getString("type").contains("custom"))
    					{
    						JSONObject entry = obj.getJSONObject("msg");
    						AnalyticsEvent event = new AnalyticsEvent();
    						event.name = entry.getString("name");
    						event.ts = Long.parseLong(entry.getString("ts"));
    						
    						System.out.println(event.name);
    						System.out.println(event.ts);
    						
    						if (entry.has("custom_params"))
    						{
    							event.custom_params = entry.getString("custom_params");
    							System.out.println(event.custom_params);
    						}
    						
    						System.out.println();
    						
    						if (!hashMap.containsKey(event.name)) 
    						{
    							hashMap.put(event.name, new PriorityQueue<AnalyticsEvent>(new StringLengthComparator()));
    							hashMap.get(event.name).add(event);
    							continue;
    						}

//    						if (hashMap.get(name).contains(custom_params)) 
//    						{
//    							continue;
//    						}
    						
    						hashMap.get(event.name).add(event);
    					}
    				
    				} catch(Exception e2) {
    					continue;
    				}
    			}
        	} catch (Exception e2) {
        		
        	}
    	}
    }
    
    @RequestMapping(value = "/events/remoteSettings/initilize", method=RequestMethod.POST)
    void remoteSettingsPostInitilize() 
    {
    	remoteSettings.clear();
    }
    
    @RequestMapping(value = "/events/remoteSettings/set", method=RequestMethod.POST)
    void remoteSettingsPost(@RequestBody String string) 
    {
    	String[] splitedRequests = string.split("%%");
    	
    	for (String request : splitedRequests)
    	{
    		if (request.length() <= 1)
    		{
    			continue;
    		}
    		
        	String[] splited = request.split("&&");
        	
        	String key = splited[0];
        	String type = splited[1];
        	
        	if (type.equals("String"))
        	{
        		remoteSettings.put(key, splited[2]);
        	}
        	
        	if (type.equals("Float"))
        	{
        		remoteSettings.put(key, Float.parseFloat(splited[2]));
        	}
        	
        	if (type.equals("Bool"))
        	{
        		remoteSettings.put(key, Boolean.parseBoolean(splited[2]));
        	}
        	
        	if (type.equals("Int"))
        	{
        		remoteSettings.put(key, Integer.parseInt(splited[2]));
        	}
    	}
    }
    
    @RequestMapping(value = "/events/remoteSettings/remove", method=RequestMethod.POST)
    void remoteSettingsRemove(@RequestBody String string) 
    {
    	remoteSettings.remove(string);
    }
    
    @RequestMapping(value = "/events/remoteSettings", method=RequestMethod.POST)
    String remoteSettingsGetNewFormat() 
    {
    	String remoteSettingsKey = "";
    	int keySetLenght = remoteSettings.keySet().size();
    	int index = 0;
    	
    	for (String key : remoteSettings.keySet())
    	{
    		if (remoteSettings.get(key).getClass() == String.class)
    		{
    			remoteSettingsKey += "\"" + key + "\": \"" + remoteSettings.get(key)+ "\"";
    		}
    		else
    		{
    			remoteSettingsKey += "\"" + key + "\": " + remoteSettings.get(key);
    		}
    		
    		if (index++ != keySetLenght - 1)
    		{
    			remoteSettingsKey += ",";
    		}
    	}
    	
    	String remoteConfig = "{\"prefs\":{"+ remoteSettingsKey +"},\"analytics\":{\"enabled\":true,\"events\":{\"custom_event\":{\"max_event_per_hour\":100000}}}}";
    	
    	return remoteConfig;
    }
    
    @RequestMapping(value = "/events/remoteSettings/{appid}.json", method=RequestMethod.GET)
    String remoteSettingsGet(@PathVariable String appid) 
    {
    	String remoteSettingsKey = "";
    	int keySetLenght = remoteSettings.keySet().size();
    	int index = 0;
    	
    	for (String key : remoteSettings.keySet())
    	{
    		if (remoteSettings.get(key).getClass() == String.class)
    		{
    			remoteSettingsKey += "\"" + key + "\": \"" + remoteSettings.get(key)+ "\"";
    		}
    		else
    		{
    			remoteSettingsKey += "\"" + key + "\": " + remoteSettings.get(key);
    		}
    		
    		if (index++ != keySetLenght - 1)
    		{
    			remoteSettingsKey += ",";
    		}
    	}
    	
    	String remoteConfig = "{\"prefs\":{"+ remoteSettingsKey +"},\"analytics\":{\"enabled\":true,\"events\":{\"custom_event\":{\"max_event_per_hour\":100000}}}}";
    	
    	return remoteConfig;
    }
    
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) 
    {
        return application.sources(StandardEventServerApplication.class);
    }
    
	public static void main(String[] args) 
	{
		SpringApplication.run(StandardEventServerApplication.class);
	}
	
	// Helper functions.
	// Get index from the queue.
	private AnalyticsEvent getIndex(int index, PriorityQueue<AnalyticsEvent> queue)
	{
		queue.toArray()
		if (index > queue.size() || index < 0)
		{
			return new AnalyticsEvent();
		}
		
		PriorityQueue<AnalyticsEvent> copy = queue;
		
		for (int i = 0; i < queue.size(); i++)
		{
			if (i == index)
			{
				return copy.peek();
			}
			else
			{
				copy.poll();
			}
		}
		
		return new AnalyticsEvent();
	}
	
	// Comparator for the PriorityQueue.
	public class StringLengthComparator implements Comparator<AnalyticsEvent>
	{
	    @Override
	    public int compare(AnalyticsEvent x, AnalyticsEvent y)
	    {
	    	if (x.ts < y.ts)
	    	{
	    		return -1;
	    	}
	    	else
	    	{
	    		return 1;
	    	}
	    }
	}
}
