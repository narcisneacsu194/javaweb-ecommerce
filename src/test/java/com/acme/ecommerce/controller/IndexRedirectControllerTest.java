package com.acme.ecommerce.controller;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IndexRedirectControllerTest {
	 
	 @InjectMocks
	 private IndexRedirectController indexRedirectController;
	 
	 private MockMvc mockMvc;
	 
	 @Before
	 public void setup() {
		 MockitoAnnotations.initMocks(this);
		 mockMvc = MockMvcBuilders.standaloneSetup(indexRedirectController).build();
	 }

	 @Test
	 public void redirectHome() throws Exception {
	  mockMvc.perform(MockMvcRequestBuilders.get("/"))
	   .andExpect(status().is3xxRedirection())
	   .andExpect(redirectedUrl("/product/"));
	 }
	 
	 @Test
	 public void redirectIndex() throws Exception {
	  mockMvc.perform(MockMvcRequestBuilders.get("/index.html"))
	   .andExpect(status().is3xxRedirection())
	   .andExpect(redirectedUrl("/product/"));
	 }
}
