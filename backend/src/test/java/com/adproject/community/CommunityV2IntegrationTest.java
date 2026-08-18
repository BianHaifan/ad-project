package com.adproject.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*; import java.util.*; import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.http.MediaType; import org.springframework.mock.web.MockMultipartFile; import org.springframework.test.context.ActiveProfiles; import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") class CommunityV2IntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
 @Test void categorizedImagePostIsSearchableAndDirectConversationIsParticipantOnly() throws Exception {
  String candidate=register("community-v2-candidate@example.com","CANDIDATE",null),recruiter=register("community-v2-recruiter@example.com","RECRUITER","Community V2 Ltd"),other=register("community-v2-other@example.com","CANDIDATE",null);
  byte[] png=new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,0,0,0,0};
  var body=new MockMultipartFile("body","","text/plain","Hiring Kotlin engineers".getBytes());var category=new MockMultipartFile("category","","text/plain","RECRUITING".getBytes());var image=new MockMultipartFile("images","role.png","image/png",png);
  String created=mvc.perform(multipart("/api/v1/community/posts").file(body).file(category).file(image).header("Authorization","Bearer "+recruiter)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.category").value("RECRUITING")).andExpect(jsonPath("$.data.images[0].contentType").value("image/png")).andReturn().getResponse().getContentAsString();
  JsonNode post=mapper.readTree(created).path("data");String postId=post.path("id").asText();String imageId=post.path("images").get(0).path("imageId").asText();
  mvc.perform(get("/api/v1/community/posts").header("Authorization","Bearer "+candidate).param("q","Kotlin").param("category","RECRUITING")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(postId));
  mvc.perform(get("/api/v1/community/posts/{postId}/images/{imageId}",postId,imageId)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  String started=mvc.perform(post("/api/v1/community/posts/{postId}/direct-conversation",postId).header("Authorization","Bearer "+candidate)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();String id=mapper.readTree(started).path("data").path("conversationId").asText();
  mvc.perform(post("/api/v1/community/direct-conversations/{id}/messages",id).header("Authorization","Bearer "+candidate).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Is this role remote?\"}")).andExpect(status().isCreated());
  mvc.perform(get("/api/v1/community/direct-conversations").header("Authorization","Bearer "+candidate)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].conversationId").value(id)).andExpect(jsonPath("$.meta.total").value(1));
  mvc.perform(get("/api/v1/community/direct-conversations").header("Authorization","Bearer "+recruiter)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].conversationId").value(id));
  mvc.perform(get("/api/v1/community/direct-conversations").header("Authorization","Bearer "+other)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
  mvc.perform(get("/api/v1/community/direct-conversations/{id}/messages",id).header("Authorization","Bearer "+recruiter)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].body").value("Is this role remote?"));
  mvc.perform(get("/api/v1/community/direct-conversations/{id}/messages",id).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
 }
 private String register(String email,String role,String company)throws Exception{var input=new LinkedHashMap<String,Object>();input.put("role",role);input.put("fullName",email);input.put("email",email);input.put("password","Password1!");input.put("acceptedTermsVersion","2026-08");if(company!=null)input.put("companyName",company);String json=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return mapper.readTree(json).path("data").path("accessToken").asText();}
}
