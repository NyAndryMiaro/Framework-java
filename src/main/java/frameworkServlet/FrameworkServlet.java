package frameworkServlet;
import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;;

public class FrameworkServlet extends HttpServlet{
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException{
        processRequest(req, res);
    }
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException{
        processRequest(req, res);
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException{
        String url = req.getRequestURL().toString();
        PrintWriter out = res.getWriter();
        out.println(url);
    }
}
