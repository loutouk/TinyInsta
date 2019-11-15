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
	private final int LIKE_COUNTER_MAX_SHARD = 2;

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
			delay *= 2; // Easy exponential backoff

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
			delay *= 2; // Easy exponential backoff
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
			delay *= 2; // Easy exponential backoff
		}

	}

	// Like a post
	// No verification: considers the call legitimate
	// Warning: no lock on the referenced post to like (might be deleted meanwhile for example)
	/* We use sharded counter(CRDT) to address the contention problem.
	We can only expect to update any single entity or entity group about five times a second.
	We arbitrarily consider that a number of 20 fragments (example) is sufficient to absorb the contention
	(20 shards * 5 writes/second is roughly equivalent to 100 likes/second). */
	@ApiMethod(name = "like", path = "like/{postId}/{parentId}/{userName}", httpMethod = ApiMethod.HttpMethod.POST)
	public Object like(@Named("postId") String postId, @Named("parentId") String parentId, @Named("userName") String userName) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true) {

			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			// Requires a transaction because the counter may be updated by another instance meanwhile
			Transaction txn = datastore.beginTransaction();
			Entity likeShard = null;

			try {

				// Using a random entity amongst many allows us to address contention on the like button (see CRDT)
				int randomCounterId = new Random().nextInt(LIKE_COUNTER_MAX_SHARD) + 1;

				Key likeShardKey = KeyFactory.createKey("LikeShard", Long.parseLong(postId) + randomCounterId);

				try {
					likeShard = datastore.get(likeShardKey);
				} catch (EntityNotFoundException e) {
					// The LikeShard with the rand id might not exist yet, so we create it
					// Creating it at this stage allows us to increase the LIKE_COUNTER_MAX_SHARD easily and only create shards when necessary
					likeShard = new Entity("LikeShard", Long.parseLong(postId) + randomCounterId);
					// Init likeShard
					likeShard.setProperty("PostId", postId);
					likeShard.setProperty("UserAndPostid", new ArrayList<>()); // Home made composite index without combinatorial explosion drawback
					likeShard.setProperty("LikesCount", new Long(0)); // Just a shortcut to sum the list above
				}
				// Now the entity should not be null
				Long likesCount = (Long) likeShard.getProperty("LikesCount");
				likeShard.setProperty("LikesCount", likesCount + 1);
				ArrayList<String> userAndPostIndex = (ArrayList<String>) likeShard.getProperty("UserAndPostid");
				if(userAndPostIndex == null) {
					userAndPostIndex = new ArrayList<>(); // Can happen we the LikeShard was created but all the likes inside have been removed
				}
				userAndPostIndex.add(userName+postId);
				likeShard.setProperty("UserAndPostid", userAndPostIndex);
				datastore.put(likeShard);
				txn.commit();
				return likeShard; // TODO stop returning entity object but return json object ERROR or OK

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
			delay *= 2; // Easy exponential backoff
		}

	}

	// Remvoe a like from a post for a given user
	// No verification: considers the call legitimate
	@ApiMethod(name = "unlike", path = "unlike/{postId}/{userName}", httpMethod = ApiMethod.HttpMethod.POST)
	public Object unlike(@Named("postId") String postId, @Named("userName") String userName) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true) {

			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			Transaction txn = datastore.beginTransaction();

			try {

				Query q = new Query("LikeShard")
						.setFilter(new FilterPredicate("UserAndPostid", FilterOperator.EQUAL, userName+postId));
				datastore = DatastoreServiceFactory.getDatastoreService();
				PreparedQuery pq = datastore.prepare(q);
				List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
				// There should be only one element because of name uniqueness + postid uniqueness
				if(result != null && result.size() > 0){
					Entity likeShard = result.get(0);
					Long likesCount = (Long) likeShard.getProperty("LikesCount");

					ArrayList<String> userAndPostIndex = (ArrayList<String>) likeShard.getProperty("UserAndPostid");
					// There should be no duplicate. Otherwise use removeAll(Collections.singleton(foo))
					if(userAndPostIndex.remove(userName+postId)){
						likeShard.setProperty("UserAndPostid", userAndPostIndex);
						likeShard.setProperty("LikesCount", likesCount - 1);
						datastore.put(likeShard);
						txn.commit();
						return likeShard; // TODO stop returning entity object but return json object ERROR or OK
					} else {
						// Not suppose to happen because we already found this index with the setFilter at this point
						return null;
					}

				} else {
					return null; // Error, like not found. Should not happen because of the lock.
				}


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
			delay *= 2; // Easy exponential backoff
		}

	}

	// Has a post been liked by a user ?
	@ApiMethod(name = "isLiked", path = "isLiked/{postId}/{userName}", httpMethod = ApiMethod.HttpMethod.GET)
	public Object isLiked(@Named("postId") String postId, @Named("userName") String userName) {
		Query q = new Query("LikeShard")
				.setFilter(new FilterPredicate("UserAndPostid", FilterOperator.EQUAL, userName+postId));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return (result != null && result.size() > 0) ? result.get(0) : null; // TODO return json ok or error
	}

	// How many likes a post has?
	@ApiMethod(name = "likesNumber", path = "likesNumber/{postId}/", httpMethod = ApiMethod.HttpMethod.GET)
	public Object likesNumber(@Named("postId") String postId) {
		Query q = new Query("LikeShard")
				.setFilter(new FilterPredicate("PostId", FilterOperator.EQUAL, postId));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		Long likesCounter = new Long(0);
		if (result != null && result.size() > 0) {

			for(Entity shard : result) {
				if(shard.hasProperty("LikesCount")){
					Long shardLikes = (Long) shard.getProperty("LikesCount");
					likesCounter = Long.sum(shardLikes, likesCounter);

				}
			}
		}
		return likesCounter;
	}


}