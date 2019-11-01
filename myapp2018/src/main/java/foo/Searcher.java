package foo;

import com.google.appengine.api.search.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// With @WebServlet annotation the webapp/WEB-INF/web.xml is no longer required.
@WebServlet("/search")
public class Searcher extends HttpServlet {

    private static final String SEARCH_INDEX = "name";

    private Index getIndex() {
        IndexSpec indexSpec = IndexSpec.newBuilder().setName(SEARCH_INDEX).build();
        Index index = SearchServiceFactory.getSearchService().getIndex(indexSpec);
        return index;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String name = request.getParameter("name");
        if(name != null){



            /*Query q = new Query("User")
                    .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUALS, name));
            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            PreparedQuery pq = datastore.prepare(q);
            List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
            if(!result.isEmpty()){
                String searchResults = new Gson().toJson(result);
                response.getWriter().write(searchResults);
            }*/

            final int maxRetry = 3;
            int attempts = 0;
            int delay = 2;
            while (true) {
                try {
                    //String queryString = name;
                    //Results<ScoredDocument> results = getIndex().search(queryString);

                    SearchService searchService = SearchServiceFactory.getSearchService();
                    Index index = searchService.getIndex(IndexSpec.newBuilder().setName("name").build());


                    Results<ScoredDocument>  results = index.search(name);
                    for (ScoredDocument document : results) {
                        // handle results
                        response.getWriter().write("name: " + document.getOnlyField("name").getText());
                    }


                } catch (SearchException e) {
                    if (StatusCode.TRANSIENT_ERROR.equals(e.getOperationResult().getCode())
                            && ++attempts < maxRetry) {
                        // retry
                        try {
                            Thread.sleep(delay * 1000);
                        } catch (InterruptedException e1) {
                            // ignore
                        }
                        delay *= 2; // easy exponential backoff
                        continue;
                    } else {
                        throw e;
                    }
                }
                break;
            }

        }
    }

}