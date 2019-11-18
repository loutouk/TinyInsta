package foo;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * handles filter to administrate which client can access which resource using session identification
 * not deployed
 *
 * @author  LASHERME Loic, FILAUDEAU Eloi, BOURSIER Louis
 * @version 1.0
 */
@WebFilter("/tofilter/*") // Filter scope, all pages matching the url pattern will have the filter applied to them
public class LoginFilter implements Filter {

    private List<String> excludedUrls = new ArrayList<>();

    @Override
    public void init(FilterConfig config) throws ServletException {
        // If you have any <init-param> in web.xml, then you could get them
        // here by config.getInitParameter("name") and assign it as field.
        // String excludePattern = config.getInitParameter("excludedUrls");
        excludedUrls.add("/log");
        excludedUrls.add("/_ah/api/myApi/v1/addUser/*");
        excludedUrls.add("/_ah/api/myApi/v1/getUser/*");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        HttpSession session = request.getSession(false);

        String path = request.getServletPath();

        if (!excludedUrls.contains(path) && (session == null || session.getAttribute("name") == null)) {
            response.sendRedirect(request.getContextPath() + "/index.html"); // No logged-in user found, so redirect to login page.
        } else {
            chain.doFilter(req, res); // Logged-in user found, so just continue request.
        }
    }

    @Override
    public void destroy() {
        // If you have assigned any expensive resources as field of
        // this Filter class, then you could clean/close them here.
    }

}
