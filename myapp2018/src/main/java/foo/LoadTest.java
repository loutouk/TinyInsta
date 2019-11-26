package foo;

import java.io.IOException;
import java.util.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.*;

@WebServlet(name = "loadtest", urlPatterns = { "/loadtest" })
public class LoadTest extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        String strResponse = "For dev tests only.";

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        final int USER_NUMBER = 15000;
        final double AVERAGE_NUMBER = 30;
        final String FOLLOWED_USER = "mainUser";
        final String FOLLOWER_USER = "user0";

        long startTime = 0;
        long delta = 0;
        long endTime = 0;

        // Create users
        ArrayList<String> mainUserFollowers = new ArrayList<>();
        /*for(int i=2000 ; i<USER_NUMBER ; i++) {


            Entity e = new Entity("User");
            e.setProperty("name", "user" + i);
            mainUserFollowers.add("user" + i);
            // Add subscribers
            e.setProperty("subscribers", new ArrayList<String>());
            // Add subscriptions
            ArrayList<String> subs = new ArrayList<>();
            subs.add(FOLLOWED_USER);
            e.setProperty("subscriptions", subs);
            e.setProperty("date", new Date());
            datastore.put(e);

        }*/

        // Add the created users in the mainUser followers
        Query q = new Query("User")
                .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, FOLLOWED_USER));
        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
        result.get(0).setProperty("subscribers", mainUserFollowers);
        datastore.put(result.get(0));


        ///////////////////////
        ///////////////////////
        // How long it takes to post of message if followed by 10, 100 and 500 followers? (average on 30 measures)
        delta = 0;
        for(int j=0 ; j<AVERAGE_NUMBER ; j++) {
            startTime = System.currentTimeMillis();

            // Retrieve the followee
            q = new Query("User")
                    .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, FOLLOWED_USER));
            pq = datastore.prepare(q);
            result = pq.asList(FetchOptions.Builder.withDefaults());
            Entity followeeEntity = result.get(0);
            // Creates Post entity with its User as parent, and let the Appengine generate the key
            Entity e = new Entity("Post", followeeEntity.getKey());
            e.setProperty("name", FOLLOWED_USER);
            String demoHashtags = "hashtagA, hashtagB, hashtagC";
            // Regex splits the string on a delimiter defined as: zero or more whitespace, a literal comma, zero or more whitespace
            ArrayList<String> hashtagList = new ArrayList<>(Arrays.asList(demoHashtags.split("\\s*,\\s*")));
            e.setProperty("hashtag", hashtagList);
            String demoImageUrl = "http://localhost:8080/_ah/img/keuCZWbcxUZZT7BLk42bKw";
            e.setProperty("image", demoImageUrl);
            e.setProperty("date", new Date());
            // TODO replace timestamp with rand value because we are inserting more than one post per millisec
            //e.setProperty("timestamp", -System.currentTimeMillis()/1000);
            e.setProperty("timestamp", new Random().nextLong());
            datastore.put(e);

            endTime = System.currentTimeMillis();
            delta += endTime-startTime;

        }

        strResponse += delta/AVERAGE_NUMBER + "\n";
        System.out.println("Post load test average on " + AVERAGE_NUMBER + ": " + delta/AVERAGE_NUMBER);


        ///////////////////////
        ///////////////////////
        ///////////////////////
        ///////////////////////
        // How long it takes to retrieve the last 10, 100 and 500 last messages ? (average of 30 measures)

        // Retrieve the followee
        /*Query q = new Query("User")
                .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, FOLLOWED_USER));
        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
        Entity followeeEntity = result.get(0);

        // Create the posts
        /*for(int i=0 ; i<USER_NUMBER ; i++) {
            // Creates Post entity with its User as parent, and let the Appengine generate the key
            Entity e = new Entity("Post", followeeEntity.getKey());
            e.setProperty("name", FOLLOWED_USER);
            String demoHashtags = "hashtagA, hashtagB, hashtagC";
            // Regex splits the string on a delimiter defined as: zero or more whitespace, a literal comma, zero or more whitespace
            ArrayList<String> hashtagList = new ArrayList<>(Arrays.asList(demoHashtags.split("\\s*,\\s*")));
            e.setProperty("hashtag", hashtagList);
            String demoImageUrl = "http://localhost:8080/_ah/img/keuCZWbcxUZZT7BLk42bKw";
            e.setProperty("image", demoImageUrl);
            e.setProperty("date", new Date());
            // TODO replace timestamp with rand value because we are inserting more than one post per millisec
            //e.setProperty("timestamp", -System.currentTimeMillis()/1000);
            e.setProperty("timestamp", new Random().nextLong());
            datastore.put(e);
        }*/

        /*delta = 0;
        for(int j=0 ; j<AVERAGE_NUMBER ; j++) {
            startTime = System.currentTimeMillis();

            /////////////////////////////////////////////////
            q = new Query("User").setFilter(new Query.FilterPredicate("subscribers", Query.FilterOperator.EQUAL, FOLLOWER_USER)).setKeysOnly();
            pq = datastore.prepare(q);
            List<Entity> userIds = pq.asList(FetchOptions.Builder.withDefaults());
            ArrayList<Entity> posts = new ArrayList<>();
            for(Entity userId : userIds){
                q = new Query("Post").setAncestor(userId.getKey());
                pq = datastore.prepare(q);
                List<Entity> userPosts = pq.asList(FetchOptions.Builder.withDefaults());
                posts.addAll(userPosts);
            }
            Collections.sort(posts, (o1, o2) -> (int)
                    (((Long) o1.getProperty("timestamp")) - ((Long) o2.getProperty("timestamp"))));
            /////////////////////////////////////////////////
//            q = new Query("User").setFilter(new Query.FilterPredicate("subscribers", Query.FilterOperator.EQUAL, FOLLOWER_USER)).setKeysOnly();
//            datastore = DatastoreServiceFactory.getDatastoreService();
//            pq = datastore.prepare(q);
//            List<Entity> userIds = pq.asList(FetchOptions.Builder.withDefaults());
//            TreeSet<Post> posts = new TreeSet<>();
//            for(Entity userId : userIds){
//                q = new Query("Post").setAncestor(userId.getKey());
//                pq = datastore.prepare(q);
//                List<Entity> userPosts = pq.asList(FetchOptions.Builder.withDefaults());
//                for(Entity postEntity : userPosts){
//                    Post postInstance = new Post();
//                    postInstance.setName((String) postEntity.getProperty("name"));
//                    postInstance.setTimestamp((Long) postEntity.getProperty("timestamp"));
//                    postInstance.setImage((String) postEntity.getProperty("image"));
//                    postInstance.setHashtag((ArrayList<String>) postEntity.getProperty("hashtag"));
//                    postInstance.setDate((Date) postEntity.getProperty("date"));
//                    posts.add(postInstance);
//                }
//            }
            /////////////////////////////////////////////////
            endTime = System.currentTimeMillis();
            delta += endTime-startTime;
        }

        strResponse += delta/AVERAGE_NUMBER + "\n";
        System.out.println("Get Post load test average on " + AVERAGE_NUMBER + ": " + delta/AVERAGE_NUMBER);*/


        ///////////////////////
        ///////////////////////
        ///////////////////////
        ///////////////////////
        // How many “likes” can you do per second ?? (average on 30 measures)


        /*String postId = "6737807255011328";
        String parentId = "5629499534213120";

        final int LIKE_COUNTER_MAX_SHARD = 32;
        final int TRANSACTION_RETRIES = 3;

        datastore = DatastoreServiceFactory.getDatastoreService();


        Thread[] th=new Thread[10];

        for (int i=0;i<th.length;i++) {
            th[i]=ThreadManager.createThreadForCurrentRequest(new Runnable()  {
                public void run() {

                    long startTime = System.currentTimeMillis();

                    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();

                    for (int j=0;j<1000;j++) {






                            String userName = "user";

                            int retries = 0;
                            long delay = 200; // Seconds before first retry

                            while (true && System.currentTimeMillis() - startTime < 1000) {

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



                                    //Thread.sleep(200);



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


                                } catch (ConcurrentModificationException e) {
                                    if (retries >= TRANSACTION_RETRIES) {
                                        System.out.println("retries >= TRANSACTION_RETRIES");
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
                                    Thread.sleep(delay);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                delay *= 2; // Easy exponential backoff
                            }

                        System.out.println("time off" + (System.currentTimeMillis() - startTime));


                    }
                }
            });
            th[i].start();
        }

        for (Thread thread : th) {
            try {
                thread.join();
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }


        Query q = new Query("LikeShard")
                .setFilter(new Query.FilterPredicate("PostId", Query.FilterOperator.EQUAL, postId));
        datastore = DatastoreServiceFactory.getDatastoreService();
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

        System.out.println("Max likes  " + likesCounter);

        strResponse = "likes: " + likesCounter;*/



        response.getWriter().write(strResponse);
    }
}