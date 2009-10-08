package com.google.yaw.mobile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.google.gdata.data.youtube.VideoEntry;
import com.google.gdata.data.youtube.YtStatistics;
import com.google.yaw.Util;
import com.google.yaw.YouTubeApiManager;
import com.google.yaw.model.AdminConfig;
import com.google.yaw.model.VideoSubmission;

public class PersistMobileSubmission extends HttpServlet {

  private static final Logger log = Logger.getLogger(PersistMobileSubmission.class.getName());

  private String decode(String input) {
    input = input.replaceAll("%26", "&");
    return input.replaceAll("%3D|%3d", "=");
  }

  private Map<String, String> processPostData(String postDataString){
    Map<String, String> submissionData = new HashMap<String, String>();
    // remove new lines
    postDataString = postDataString.replaceAll("\\n", "");
    String[] nameValuePairs = postDataString.split("&");
    for (String nameValuePair : nameValuePairs) {
      String[] tuple = nameValuePair.split("=");
      if (tuple.length >= 2) {
        String name = decode(tuple[0]);
        String value = decode(tuple[1]);
        log.info(name);
        log.info(value);
        submissionData.put(name, value);
      } else {
        submissionData = null;    
      }
    }    
    return submissionData;
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      Map<String, String> submissionData = processPostData(Util.getPostBody(req));      
      if (submissionData == null) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid post data format");
      }
      
      long assignmentId = -1;
      String videoId = null;
      String location = null;
      String date = null;
      String authSubToken = null;
      String email = null;
      
      assignmentId = submissionData.get("assignmentId") != null ? Long.parseLong(submissionData
          .get("assignmentId")) : -1;
      videoId = submissionData.get("videoId");
      location = submissionData.get("location");
      date = submissionData.get("date");
      authSubToken = submissionData.get("authSubToken");
      email = submissionData.get("email");
  
      if (assignmentId <= 0) {
        // get default mobile assignment ID
        assignmentId = Util.getDefaultMobileAssignmentId();
      }
      if (Util.isNullOrEmpty(videoId)) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing videoId");
      }
      if (Util.isNullOrEmpty(authSubToken)) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing authSubToken");
      }
  
      YouTubeApiManager apiManager = new YouTubeApiManager();      
      apiManager.setToken(authSubToken);
      VideoEntry videoEntry = apiManager.getUploadsVideoEntry(videoId);
      
      if (videoEntry == null) {        
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, 
            "video id does not match the authsub token");
      } else {      
        String youTubeName = videoEntry.getAuthors().get(0).getName();        
        String title = videoEntry.getTitle().getPlainText();
        String description = videoEntry.getMediaGroup().getDescription().getPlainTextContent();        
        List<String> tags = videoEntry.getMediaGroup().getKeywords().getKeywords();
        String sortedTags = Util.sortedJoin(tags, ",");
        
        VideoSubmission submission = new VideoSubmission(assignmentId);
        submission.setVideoId(videoId);
        submission.setVideoTitle(title);
        submission.setVideoDescription(description);
        submission.setVideoTags(sortedTags);
        submission.setVideoLocation(location);
        submission.setVideoDate(date);
        submission.setYouTubeName(youTubeName);
        // Note: the call to setAuthSubToken needs to be made after the call to setYouTubeName,
        // since setAuthSubToken relies on a youtubeName being set in order to proxy to the
        // UserAuthToken class.
        submission.setAuthSubToken(authSubToken);
        submission.setVideoSource(VideoSubmission.VideoSource.MOBILE_SUBMIT);      
        submission.setNotifyEmail(email);
  
        AdminConfig adminConfig = Util.getAdminConfig();      
        if (adminConfig.getModerationMode() == AdminConfig.ModerationModeType.NO_MOD.ordinal()) {
          // NO_MOD is set, auto approve all submission
          //TODO: This isn't enough, as the normal approval flow (adding the branding, tags, emails,
          // etc.) isn't taking place.
            submission.setStatus(VideoSubmission.ModerationStatus.APPROVED);
        }
        Util.persistJdo(submission);
        Util.sendNewSubmissionEmail(submission);
  
        resp.setContentType("text/plain");
        resp.getWriter().println("success");
      }
    } catch (IllegalArgumentException e) {
      log.log(Level.FINE, "", e);
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    }
  }
}