package com.menyi.aio.web.mobile;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Session;
import org.hibernate.jdbc.Work;

import com.dbfactory.Result;
import com.dbfactory.hibernate.DBUtil;
import com.dbfactory.hibernate.IfDB;
import com.google.gson.Gson;
import com.koron.wechat.common.oauth2.Oauth2;
import com.koron.wechat.common.oauth2.Oauth2ResultBean;
import com.koron.wechat.common.util.WXSetting;
import com.menyi.aio.web.login.LoginBean;
import com.menyi.aio.web.usermanage.UserMgt;

public class MobileVueFilter extends HttpServlet {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	UserMgt userMgt = new UserMgt();

	
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String url = request.getRequestURI();
        if (check(url, response)) // 检查是否是微信公众平台回调验证
        	return ;
		String userAgent = request.getHeader("user-agent").toLowerCase();
		LoginBean loginBean = (LoginBean) request.getSession().getAttribute("LoginBean");
		if ((userAgent.indexOf("micromessenger") >=0 || userAgent.indexOf("wxwork") >= 0) && loginBean == null) { // 微信浏览器
			String code = request.getParameter("code");
			String state = request.getParameter("state");
			// 默认的keyName为企业微信
			Oauth2 oauth2 = new Oauth2(WXSetting.AGENTKEYNAME_WXWORK);
			// 尝试根据state获取keyName(keyName可以唯一标识微信服务号，微信企业号，企业微信)
			if (state != null && WXSetting.getInstance().getSettingBase(state) != null)
				oauth2 = new Oauth2(state);
			if (code == null) {
				// 重定向换取code
				String queryString = request.getQueryString();
				if(queryString != null)
					 url += "?"+ queryString;
				String redirectURL = oauth2.getCodeUrl(url);
				response.sendRedirect(redirectURL);
				return ;
			} else {
				// 用code换取openid 
				Oauth2ResultBean oauth2User = oauth2.getUserIdByCode(code);
				String userId = oauth2User.getUserId();
				if (userId == null && oauth2User.getOpenid() != null) { // 来源于微信，取openid
					userId = oauth2User.getOpenid();
					request.getSession().setAttribute("wxopenid", oauth2User.getOpenid());
				}
				if (oauth2User != null && userId != null) {
					Result rs = userMgt.login(userId);
					Message msg = new AIOApi().loginAuth(request, response,rs, userId);
					if( msg.getCode().equals("OK") ) {
						request.getSession().setAttribute("wxstate", oauth2.getKeyName()); // 直接使用keyName 作为state,以区分来源
						if(oauth2.getKeyName().equals(WXSetting.AGENTKEYNAME_WXWORK))// 如果是企业微信，告诉客户端需要重刷一次页面，这是为了解决当前版本jssdk的bug.
							request.setAttribute("reloadFlag", new Boolean(true)); 
					}
				}
			}	
		}
		if(request.getSession().getAttribute("LoginBean") != null) {
		    loginBean = (LoginBean) request.getSession().getAttribute("LoginBean");
			Map<String, Object> userMap = new HashMap<String, Object>();
			userMap.put("name", loginBean.getName());
			userMap.put("password", "");
			userMap.put("id", "signed");
			request.setAttribute("user", new Gson().toJson(userMap));
		}
		System.out.println(request.getContextPath() + "/mobile/mhome.jsp");
		request.getRequestDispatcher(request.getContextPath() + "/mobile/mhome.jsp").forward(request, response);
  }
	
  /**
   * 从数据库拉取微信id和secret配置	
   */
  @Override	
  public void init() {
	  DBUtil.execute(new IfDB() {
			public int exec(Session session) {
				session.doWork(new Work() {
					public void execute(Connection conn) throws SQLException {
						try {
							PreparedStatement ps = conn.prepareStatement("select * from tblWxSet");
							ResultSet rset = ps.executeQuery();
							while (rset.next())
								WXSetting.getInstance().register(rset.getString("KeyName"), rset.getString("CorpID"), rset.getString("Secret"), Integer.parseInt(rset.getString("AgentId_check")), rset.getString("RemoteUrl"), Boolean.parseBoolean(rset.getString("Flag")));
						} catch (Exception ex) {
							System.out.println("初始化微信出错");
							return;
						}
					}
				});
				return 1;
			}
		});
  }
  
  /**
   * 回应微信的回调地址验证
   * @param url
   * @param response
   * @return
   */
  public boolean check(String url, HttpServletResponse response) {
	  String regEx = "/mobilevue/MP_verify_(.+)\\.txt$";  
      Pattern p = Pattern.compile(regEx);  
      Matcher m = p.matcher(url);  
      if (m.find()) {  
    	  try
    	  {
	         response.setContentType("text/plain;charset=UTF-8");
	         response.setHeader("Content-Disposition", "attachment; filename=" + "MP_verify_" + m.group(1) + ".txt"); 
	         OutputStream outputStream = response.getOutputStream();
	         String context = m.group(1);
	         byte[] buffer = context.getBytes();  
	         outputStream.write(buffer);
	         outputStream.flush();
	         outputStream.close();
    	  }
    	  catch(Exception e)
    	  {
    	   System.out.println("微信公众平台回调验证回传verify文件错误");
    	  } 
    	  return true;
      }
      else 
    	  return false;
  }
  
}
