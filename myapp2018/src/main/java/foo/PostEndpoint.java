package foo;


import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

import java.util.*;


@Api(name = "myApi", version = "v1", namespace = @ApiNamespace(ownerDomain = "helloworld.example.com", ownerName = "helloworld.example.com", packagePath = ""))

public class PostEndpoint {

	private final int TRANSACTION_RETRIES = 3; // At most 3 tries for commiting a transaction

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
	@ApiMethod(name = "addUser", path = "addUser", httpMethod = ApiMethod.HttpMethod.POST)
	public Entity addUser(@Named("name") String name) {

		int retries = 0;
		int delay = 1; // seconds before first retry

		while (true) {

			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			// Requires transaction in case a user is created with the same name between the verifying operation and the creation
			Transaction txn = datastore.beginTransaction();

			try {

				Query q = new Query("User")
						.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));
				// Check if the user does not already exist with the same name
				PreparedQuery pq = datastore.prepare(q);
				List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
				if(result != null && result.size() > 0){
					return null;
				}

				Entity e = new Entity("User");
				e.setProperty("name", name);
				e.setProperty("subscribers", new ArrayList<String>());
				e.setProperty("subscriptions", new ArrayList<String>());
				e.setProperty("date", new Date());
				datastore.put(e);

				txn.commit();
				return e;

			} catch (ConcurrentModificationException e) {
				if (retries >= TRANSACTION_RETRIES) {
					throw e;
				}
				// Allow retry to occur
				++retries;
			} finally {
				if (txn.isActive()) {
					txn.rollback();
				}
			}

			try {
				Thread.sleep(delay * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			delay *= 2; // easy exponential backoff

		}

	}

	// Faster: Retrieves only the name property of the user
	@ApiMethod(name = "getUserLight", path = "getUserLight/{name}", httpMethod = ApiMethod.HttpMethod.GET)
	public Object getUserLight(@Named("name") String name) {
		Query q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		// There should be only one element in the list because of name uniqueness
		return (result != null && result.size() > 0) ? result.get(0).getProperty("name") : null; // In case user is not found
	}

	// Retrieves every properties fom the user
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

	@ApiMethod(name = "getUserSubscriptions", path = "getUserSubscriptions/{name}", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getUserSubscriptions(@Named("name") String name) {
		Query q = new Query("User")

				.addProjection(new PropertyProjection("subscriptions", String.class))
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);

		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}


	// Does user A follows user B?
	@ApiMethod(name = "isSubscribed", path = "isSubscribed/{userA}/{userB}", httpMethod = ApiMethod.HttpMethod.GET)
	public Object isSubscribed(@Named("userA") String userA, @Named("userB") String userB) {
		Query q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, userA));

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);

		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

		if(result!=null && result.size()>0){
			Entity user = result.get(0);
			if(user.getProperty("subscriptions") != null){
				ArrayList<String> subscriptions = (ArrayList<String>) user.getProperty("subscriptions");
				if(subscriptions != null && subscriptions.contains(userB)){
					return user;
				}
			}
		}

		return null;
	}

	// Makes a follower follow a followee.
	// Maintains the subscribers and followers list in both followwee and follower entity to optimize access later on
	@ApiMethod(name = "followUser", path = "followUser/{follower}/{followee}", httpMethod = ApiMethod.HttpMethod.GET)
	public Entity followUser(@Named("follower") String follower, @Named("followee") String followee) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true) {

			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			// Requires a transaction in case the userA already un/follow userB meanwhile
			/* Cross-group transactions (also called XG transactions) operate across multiple entity groups,
			behaving like single-group transactions described above except that cross-group transactions
			don't fail if code tries to update entities from more than one entity group. */
			TransactionOptions options = TransactionOptions.Builder.withXG(true);
			Transaction txn = datastore.beginTransaction(options);

			try {

				Query q = new Query("User")
						.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, followee));
				// Retrieve the followee
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

				txn.commit();

				return followeeEntity;

			} catch (ConcurrentModificationException e) {
				if (retries >= TRANSACTION_RETRIES) {
					throw e;
				}
				// Allow retry to occur
				++retries;
			} finally {
				if (txn.isActive()) {
					txn.rollback();
				}
			}

			try {
				Thread.sleep(delay * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			delay *= 2; // easy exponential backoff
		}

	}

	// Removes a follower from a followee subscribers
	// Maintains the subscribers and followers list in both followwee and follower entity to optimize access later on
	@ApiMethod(name = "unfollowUser", path = "unfollowUser/{follower}/{followee}", httpMethod = ApiMethod.HttpMethod.GET)
	public Entity unfollowUser(@Named("follower") String follower, @Named("followee") String followee) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true) {

			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			// Requires a transaction in case the userA already un/follow userB meanwhile
			TransactionOptions options = TransactionOptions.Builder.withXG(true);
			Transaction txn = datastore.beginTransaction(options);

			try {

				// Retrieve the followee
				Query q = new Query("User")
						.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, followee));
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
				subscribers.removeAll(Collections.singleton(follower)); // There may be duplicates in the names
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
				subscribers.removeAll(Collections.singleton(followee)); // There may be duplicates in the names
				followerEntity.setProperty("subscriptions", subscribers);
				datastore.put(followerEntity);

				txn.commit();

				return followeeEntity;

			} catch (ConcurrentModificationException e) {
				if (retries >= TRANSACTION_RETRIES) {
					throw e;
				}
				// Allow retry to occur
				++retries;
			} finally {
				if (txn.isActive()) {
					txn.rollback();
				}
			}

			try {
				Thread.sleep(delay * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			delay *= 2; // easy exponential backoff
		}

	}

}