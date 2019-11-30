package foo;


import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * the endpoint for the application's API. User can access post, subscriptions, users...
 * JSON fields in the response object are automatically mapped from the properties in the return value by the cloud endpoint
 * the API does not handle the creation of a publication
 * no verification on the identity or rights of the caller: considers all the call legitimate
 * consider implementing OAuth 2.0 to address this problem
 * @see foo.BlobstoreUploadUrlServlet#doGet(HttpServletRequest, HttpServletResponse)
 *
 * @author  LASHERME Loic, FILAUDEAU Eloi, BOURSIER Louis
 * @version 1.0
 */
@Api(name = "myApi", version = "v1", namespace = @ApiNamespace(ownerDomain = "helloworld.example.com", ownerName = "helloworld.example.com", packagePath = ""))
public class PostEndpoint {

	private final int TRANSACTION_RETRIES = 3; // The number of tries for a transaction to commit
	private final int LIKE_COUNTER_MAX_SHARD = 20; // Number of shards for a like counter, can be increased to allow more load

	/**
	 * for demonstration mainly. Not supposed to be used after deployment
	 * @return all the posts
	 */
	@Deprecated
	@ApiMethod(name = "getposts", path = "posts", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getAllPost() {
		Query q = new Query("Post").addSort("date");
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}

	/**
	 *
	 * sorting on the date needs to be done manually on this side because the result is a set of query
	 * so it can not be sorted on an entity property (the timestamp date) with the appengine Query sorting method
	 * not using a parent child relationship would allow for a single query and an easy sort on the timestamp property
	 * but because we can consider that a user does not need to load thousands of its posts instantly, this is not a problem
	 * and this architecture makes easier the process of mapping users to their subscriptions posts
	 *
	 * @param userName the name of the user
	 * @return all posts for a given user, considering its subscriptions
	 */
	@ApiMethod(name = "getsubscriberposts", path = "subscriberposts/{userName}", httpMethod = ApiMethod.HttpMethod.GET)
	public ArrayList<Entity> getSubscriberPost(@Named("userName") String userName) {
		// A keys-only query returns just the keys of the result entities instead of the entities themselves, at lower latency and cost than retrieving entire entities
		Query q = new Query("User").setFilter(new FilterPredicate("subscribers", FilterOperator.EQUAL, userName)).setKeysOnly();
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> userIds = pq.asList(FetchOptions.Builder.withDefaults());

		// Abort if no followees are found
		if(userIds == null || userIds.size() < 1){
			return null;
		}

		// Alternative choice
		// Use TreeSet to sort on insertion (on timestamp field) in O(log(n))
		// Faster than sorting a list in O(nlog(n)) with Collections.sort
		// Not mandatory because the list returned is small (maybe this approach is even slower for few Post in the list)
		// Load tests and profiling show no difference in time to fetch from 10 to 10000 posts

		ArrayList<Entity> posts = new ArrayList<>();
		//TreeSet<Post> posts = new TreeSet<>();

		for(Entity userId : userIds){
			q = new Query("Post").setAncestor(userId.getKey());
			pq = datastore.prepare(q);
			List<Entity> userPosts = pq.asList(FetchOptions.Builder.withDefaults());
			/*for(Entity postEntity : userPosts){
				Post postInstance = new Post();
				postInstance.setName((String) postEntity.getProperty("name"));
				postInstance.setTimestamp((Long) postEntity.getProperty("timestamp"));
				postInstance.setImage((String) postEntity.getProperty("image"));
				postInstance.setHashtag((ArrayList<String>) postEntity.getProperty("hashtag"));
				postInstance.setDate((Date) postEntity.getProperty("date"));
				posts.add(postInstance);
			}*/
			posts.addAll(userPosts);
		}

		Collections.sort(posts, (o1, o2) -> (int) (((Long) o1.getProperty("timestamp")) - ((Long) o2.getProperty("timestamp"))));

		return posts;
	}

	/**
	 *
	 * @param userName the name of the user
	 * @return all posts that belong to the specified user
	 */
	@ApiMethod(name = "getuserposts", path = "userposts/{userName}", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getUserPost(@Named("userName") String userName) {
		Query q = new Query("Post")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, userName)).addSort("date", Query.SortDirection.DESCENDING);
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}

	/**
	 *
	 * @param hashtag
	 * @return all posts that contain the specified hastag
	 */
	@ApiMethod(name = "gethashtagpost", path = "hashtagpost/{hashtag}", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getHashtagPost(@Named("hashtag") String hashtag) {
		Query q = new Query("Post")
				.setFilter(new FilterPredicate("hashtag", FilterOperator.EQUAL, hashtag)).addSort("date", Query.SortDirection.DESCENDING);
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}

	/**
	 * creates a user
	 * @param name the user name
	 * @return the user entity
	 */
	@ApiMethod(name = "postuser", path = "user", httpMethod = ApiMethod.HttpMethod.POST)
	public Entity addUser(@Named("name") String name) {

		int retries = 0;
		int delay = 1; // seconds before first retry

		while (true && retries<=TRANSACTION_RETRIES) {

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

		return null;
	}

	/**
	 * faster: only retrieves the name property of the user
	 * @param name
	 * @return the name property of the user
	 */
	@ApiMethod(name = "getuser", path = "user/{name}", httpMethod = ApiMethod.HttpMethod.GET)
	public Object getUserLight(@Named("name") String name) {
		Query q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		// There should be only one element in the list because of name uniqueness
		return (result != null && result.size() > 0) ? result.get(0).getProperty("name") : null; // In case user is not found
	}

	/**
	 * Not used in deployment, because returning the whole lists of subscribers and followers is not scalable
	 * @param name
	 * @return every properties fom the user
	 */
	@Deprecated
	@ApiMethod(name = "getfulluser", path = "fulluser/{name}", httpMethod = ApiMethod.HttpMethod.GET)
	public Entity getFullUser(@Named("name") String name) {
		Query q = new Query("User")
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		// There should be only one element in the list because of name uniqueness
		return (result != null && result.size() > 0) ? result.get(0) : null; // In case user is not found
	}

	/**
	 *
	 * @param name
	 * @return the list of users a user is subscribed to
	 */
	@ApiMethod(name = "getusersubscriptions", path = "usersubscriptions/{name}", httpMethod = ApiMethod.HttpMethod.GET)
	public List<Entity> getUserSubscriptions(@Named("name") String name) {
		Query q = new Query("User")

				.addProjection(new PropertyProjection("subscriptions", String.class))
				.setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name));

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);

		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		return result;
	}

	/**
	 * does user A follows user B?
	 * @param userA
	 * @param userB
	 * @return null if he does not follow, an object message if he follows
	 */
	@ApiMethod(name = "issubscribed", path = "subscriptions/{userA}/{userB}", httpMethod = ApiMethod.HttpMethod.GET)
	public ReturnMessage isSubscribed(@Named("userA") String userA, @Named("userB") String userB) {
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
					ReturnMessage msg = new ReturnMessage();
					msg.setMessage("ok");
					return msg;
				}
			}
		}

		return null;
	}

	/**
	 * makes a follower follow a followee
	 * PUT method is used because the operation is idempotent (if we consider the operation call legitimate)
	 * maintains the subscribers and followers list in both followwee and follower entity to optimize access later on
	 * @param follower
	 * @param followee
	 * @return object message if the operation is successful, null otherwise
	 */
	@ApiMethod(name = "followUser", path = "subscriptions/{follower}/{followee}", httpMethod = ApiMethod.HttpMethod.PUT)
	public ReturnMessage followUser(@Named("follower") String follower, @Named("followee") String followee) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true && retries<=TRANSACTION_RETRIES) {

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
				ReturnMessage msg = new ReturnMessage();
				msg.setMessage("ok");
				return msg;

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

		ReturnMessage msg = new ReturnMessage();
		msg.setMessage("notok");
		return msg;
	}

	/**
	 * removes a follower from a followee subscribers
	 * @param follower
	 * @param followee
	 * @return object message if the operation is successful, null otherwise
	 */
	@ApiMethod(name = "unfollowUser", path = "subscriptions/{follower}/{followee}", httpMethod = ApiMethod.HttpMethod.DELETE)
	public ReturnMessage unfollowUser(@Named("follower") String follower, @Named("followee") String followee) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true && retries<=TRANSACTION_RETRIES) {

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
				ReturnMessage msg = new ReturnMessage();
				msg.setMessage("ok");
				return msg;

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

		ReturnMessage msg = new ReturnMessage();
		msg.setMessage("notok");
		return msg;
	}

	/**
	 * like a post
	 * PUT method is used because the operation is idempotent (if we consider the operation call legitimate)
	 * warning: no lock on the referenced post to like (might be deleted meanwhile for example)
	 * use sharded counter (CRDT) to address the contention problem
	 * we can only expect to update any single entity or entity group about five times a second
	 * we arbitrarily consider that a number of 20 fragments (example) is sufficient to absorb the contention
	 * 20 shards * 5 writes/second is roughly equivalent to 100 likes/second)
	 * @param postId the id field for the post datastore object
	 * @param parentId the parent id of the post: it is the id of the user who published it
	 * @param userName the name of the user
	 * @return
	 */
	@ApiMethod(name = "like", path = "likes/{postId}/{parentId}/{userName}", httpMethod = ApiMethod.HttpMethod.PUT)
	public ReturnMessage like(@Named("postId") String postId, @Named("parentId") String parentId, @Named("userName") String userName) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true && retries<=TRANSACTION_RETRIES) {

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
				ReturnMessage msg = new ReturnMessage();
				msg.setMessage("ok");
				return msg;

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

		ReturnMessage msg = new ReturnMessage();
		msg.setMessage("notok");
		return msg;
	}

	/**
	 * remove a like from a post for a given user
	 * @param postId the id field for the post datastore object
	 * @param userName the name of the user
	 * @return
	 */
	@ApiMethod(name = "unlike", path = "likes/{postId}/{userName}", httpMethod = ApiMethod.HttpMethod.DELETE)
	public ReturnMessage unlike(@Named("postId") String postId, @Named("userName") String userName) {

		int retries = 0;
		int delay = 1; // Seconds before first retry

		while (true && retries<=TRANSACTION_RETRIES) {

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
						ReturnMessage msg = new ReturnMessage();
						msg.setMessage("ok");
						return msg;
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
				++retries; // Allow retry to occur
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

		ReturnMessage msg = new ReturnMessage();
		msg.setMessage("notok");
		return msg;
	}

	/**
	 * Has a post been liked by a user ?
	 * @param postId the id field for the post datastore object
	 * @param userName the name of the user
	 * @return null if the post is liked, an object otherwise
	 */
	@ApiMethod(name = "isLiked", path = "likes/{postId}/{userName}", httpMethod = ApiMethod.HttpMethod.GET)
	public ReturnMessage isLiked(@Named("postId") String postId, @Named("userName") String userName) {
		Query q = new Query("LikeShard")
				.setFilter(new FilterPredicate("UserAndPostid", FilterOperator.EQUAL, userName+postId));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		ReturnMessage msg = new ReturnMessage();
		msg.setMessage("ok");
		return (result != null && result.size() > 0) ? msg : null;
	}

	/** How many likes a post has?
	 * @param postId the id field for the post datastore object
	 * @return the number of likes the post has
	 */
	@ApiMethod(name = "likesNumber", path = "likesnumber/{postId}/", httpMethod = ApiMethod.HttpMethod.GET)
	public Object likesNumber(@Named("postId") String postId) {
		Query q = new Query("LikeShard")
				.setFilter(new FilterPredicate("PostId", FilterOperator.EQUAL, postId));
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
		Long likesCounter = 0L;
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
