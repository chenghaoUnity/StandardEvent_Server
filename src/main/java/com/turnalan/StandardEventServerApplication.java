package com.turnalan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;

@RestController
@EnableAutoConfiguration
@SpringBootApplication
@EnableAsync
public class StandardEventServerApplication extends SpringBootServletInitializer 
{
	HashMap<String, ArrayList<String>> hashMap = new HashMap<>();
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
			
			for (int i = 0; i < hashMap.get(key).size(); i++) 
			{
				sb.append(hashMap.get(key).get(i));
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
    	guidStatus.clear();
    	waitingQueue.clear();
		curUser = null;
		lastUserAcitityTime = null;
		LastRequestOnQueueTime = null;
		return "done";
    }
    
    @RequestMapping(value = "/events/{name}/{order}", method=RequestMethod.GET)
    String GetEvent(@PathVariable String name, @PathVariable int order) 
    {
    	if (curUser != null)
    	{
    		lastUserAcitityTime = LocalTime.now();
    	}
    	
    	if (!hashMap.containsKey(name)) 
    	{
    		return "none";
    	}
    	
    	if (order >= hashMap.get(name).size()) 
    	{
    		return "none";
    	}
    	
    	return hashMap.get(name).get(order);
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
    	if (curUser == null)
    	{
    		return;
    	}
    	
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
						String name = entry.getString("name");
						String custom_params = null;
						
						System.out.println(name);
						
						if (entry.has("custom_params"))
						{
							custom_params = entry.getString("custom_params");
							System.out.println(entry.getString("custom_params"));
						}
						
						System.out.println();
						
						if (!hashMap.containsKey(name)) 
						{
							hashMap.put(name, new ArrayList<String>());
							hashMap.get(name).add(custom_params);
							continue;
						}

						if (hashMap.get(name).contains(custom_params)) 
						{
							continue;
						}
						
						hashMap.get(name).add(custom_params);
					}
				
				} catch(Exception e) {
					
				}
			}
    	} catch (Exception e) {
    		
    	}
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
}
