package foo;


import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Api(name = "myApi", version = "v1", namespace = @ApiNamespace(ownerDomain = "helloworld.example.com", ownerName = "helloworld.example.com", packagePath = ""))

public class PostEndpoint {

	// Returns every posts
	@ApiMethod(name = "getAllPost", path = "getAllPost", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getAllPost() {
		Query q = new Query("Post");
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}

	// Returns all posts for a given user, considering its subscriptions
	@ApiMethod(name = "getSubscriberPost", path = "getSubscriberPost/{userName}", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getSubscriberPost(@Named("userName") String userName) {
		// A keys-only query returns just the keys of the result entities instead of the entities themselves, at lower latency and cost than retrieving entire entities
		Query q = new Query("User").setFilter(new FilterPredicate("subscribers", FilterOperator.EQUAL, userName)).setKeysOnly();
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> userIds = pq.asList(FetchOptions.Builder.withDefaults());

		// Abort if no followees are found
		if(userIds == null || userIds.size() < 1){
			return null;
		}

		ArrayList<Entity> posts = new ArrayList<>();
		// Use the fact that User are Post's parents: retrieves all posts that got the followee User key as parent
		for(Entity userId : userIds){
			q = new Query("Post").setAncestor(userId.getKey());
			pq = datastore.prepare(q);
			List<Entity> userPosts = pq.asList(FetchOptions.Builder.withDefaults());
			posts.addAll(userPosts);
		}
		return posts;
	}

	// Returns all posts that belong to the specified user
	@ApiMethod(name = "getUserPost", path = "getUserPost/{userName}", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getUserPost(@Named("userName") String userName) {
		Query q = new Query("Post")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, userName));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}

	// Returns all posts that contain the specified hastag
	@ApiMethod(name = "getHashtagPost", path = "getHashtagPost/{hashtag}", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getHashtagPost(@Named("hashtag") String hashtag) {
		Query q = new Query("Post")
				.setFilter(new FilterPredicate("hashtag", FilterOperator.EQUAL, hashtag));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}

	// Creates a user
	@ApiMethod(name = "addUser", path = "addUser/{name}", httpMethod = ApiMethod.HttpMethod.PUT)
	public Entity addUser(@Named("name") String name) {
		// Check if the user does not already exist with the same name
		Query q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		if(result != null && result.size() > 0){
			return null;
		}

		Entity e = new Entity("User");
		e.setProperty("name", name);
		e.setProperty("subscribers", new ArrayList<String>());
		e.setProperty("date", new Date());
		datastore = DatastoreServiceFactory.getDatastoreService();
		datastore.put(e);
		return e;
	}


	@ApiMethod(name = "getUser", path = "getUser/{name}", httpMethod = ApiMethod.HttpMethod.GET)
	public Entity getUser(@Named("name") String name) {
		Query q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		// There should be only one element in the list because of name uniqueness
		return (result != null && result.size() > 0) ? result.get(0) : null; // In case user is not found
	}



	// Makes a follower follow a followee.
	// Maintains the subscribers and followers list in both followwee and follower entity to optimize access later on
	@ApiMethod(name = "followUser", path = "followUser/{follower}/{followee}", httpMethod = ApiMethod.HttpMethod.GET)
	public Entity followUser(@Named("follower") String follower, @Named("followee") String followee) {

		// Retrieve the followee
		Query q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, followee));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

		// Abort if the followee does not exist
		if(result == null || result.size() < 1){
			return null;
		}

		// There should be only one element in the list because of name uniqueness
		Entity followeeEntity = result.get(0);
		// Adding the follower to its subscribers
		ArrayList<String> subscribers = (ArrayList<String>) followeeEntity.getProperty("subscribers");
		if(subscribers == null){
			subscribers = new ArrayList<>();
		}
		subscribers.add(follower);
		followeeEntity.setProperty("subscribers", subscribers);
		datastore.put(followeeEntity);


		// Retrieve the follower
		q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, follower));
		datastore = DatastoreServiceFactory.getDatastoreService();
		pq = datastore.prepare(q);
		result = pq.asList(FetchOptions.Builder.withDefaults());

		// Abort if the follower does not exist
		if(result == null || result.size() < 1){
			return null;
		}

		// There should be only one element in the list because of name uniqueness
		Entity followerEntity = result.get(0);
		// Adding the follower to its subscribers
		subscribers = (ArrayList<String>) followerEntity.getProperty("subscriptions");
		if(subscribers == null){
			subscribers = new ArrayList<>();
		}
		subscribers.add(followee);
		followerEntity.setProperty("subscriptions", subscribers);
		datastore.put(followerEntity);

		return followeeEntity;
	}




}