package foo;

import com.google.appengine.api.datastore.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * handles the connection of a user, and its session
 *
 * @author  LASHERME Loic, FILAUDEAU Eloi, BOURSIER Louis
 * @version 1.0
 */
@WebServlet("Log")
public class Log extends HttpServlet {
    /**
     * @see foo.Log#doPost(HttpServletRequest, HttpServletResponse)
     * @param req
     * @param res
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        doPost(req, res);
    }

    /**
     * log in the user and creates its session if it exists in the datastore
     * @param req
     * @param res
     * @throws IOException
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {




        if(req.getSession(false) != null && req.getSession(false).getAttribute("name") != null){
            res.sendRedirect(req.getContextPath() + "/home.html"); // Already logged in
        } else if(req.getParameter("name") != null){

            Query q = new Query("User")
                    .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, req.getParameter("name")));
            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            PreparedQuery pq = datastore.prepare(q);
            List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
            // There should be only one element in the list because of name uniqueness
            if (result.isEmpty()) {
                res.sendRedirect(req.getContextPath() + "/index.html"); // User does not exist
            }else{
                req.getSession().setAttribute("name", req.getParameter("name"));
                res.sendRedirect(req.getContextPath() + "/home.html"); // Create session
            }
        }else{
            res.sendRedirect(req.getContextPath() + "/index.html"); // Not logged in, redirect to login page
        }

    }

}

