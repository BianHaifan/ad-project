package com.adproject.onboarding;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CandidateOnboardingIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;

    @Test
    void candidateCompletesAllThreeResourcesAtomicallyAndLoginNoLongerRequiresOnboarding() throws Exception {
        String email="onboarding@example.com",password="Password1!";
        JsonNode auth=register(email,password,"CANDIDATE",null);
        String token=auth.path("accessToken").asText();
        org.assertj.core.api.Assertions.assertThat(auth.path("onboardingRequired").asBoolean()).isTrue();
        Map<String,Object> body=Map.of("headline","Backend Engineer","location","Singapore","age",28,
                "resumeSummary","Java engineer building reliable APIs.","skills",java.util.List.of("Java","Spring Boot"),
                "desiredTitle","Backend Engineer","preferredLocation","Singapore","workplaceType","HYBRID","employmentType","FULL_TIME");
        mvc.perform(post("/api/v1/candidate/onboarding").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/candidate/profile").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.age").value(28));
        mvc.perform(get("/api/v1/candidate/resume").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.skills[0]").value("Java"));
        mvc.perform(get("/api/v1/candidate/job-preferences").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.desiredTitles[0]").value("Backend Engineer"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",email,"password",password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.onboardingRequired").value(false));
    }

    @Test void recruiterCannotCompleteCandidateOnboarding() throws Exception {
        JsonNode auth=register("onboarding-recruiter@example.com","Password1!","RECRUITER","Onboarding Ltd");
        mvc.perform(post("/api/v1/candidate/onboarding").header("Authorization","Bearer "+auth.path("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"headline\":\"Recruiter\",\"location\":\"Singapore\",\"age\":30,\"resumeSummary\":\"Summary\",\"skills\":[\"Hiring\"],\"desiredTitle\":\"Recruiter\",\"preferredLocation\":\"Singapore\",\"workplaceType\":\"HYBRID\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isForbidden());
    }

    private JsonNode register(String email,String password,String role,String company) throws Exception {
        var body=new java.util.LinkedHashMap<String,Object>();body.put("role",role);body.put("fullName","Onboarding User");body.put("email",email);body.put("password",password);body.put("acceptedTermsVersion","2026-08");if(company!=null)body.put("companyName",company);
        String value=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return mapper.readTree(value).path("data");
    }
}
