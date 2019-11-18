package foo;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// With @WebServlet annotation the webapp/WEB-INF/web.xml is no longer required.
/**
 * deals with the client session. Usefull to identify the client on the front end.
 *
 * @author  LASHERME Loic, FILAUDEAU Eloi, BOURSIER Louis
 * @version 1.0
 */
@WebServlet("/session")
public class Session extends HttpServlet {

    /**
     * returns the client session if he has one
     * @param request
     * @param response
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        if(request.getSession(false) == null){
            response.getWriter().write("");
        }else{
            response.getWriter().write((String) request.getSession().getAttribute("name"));
        }
    }

}