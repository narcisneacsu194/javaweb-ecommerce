package com.acme.ecommerce.controller;

import com.acme.ecommerce.domain.*;
import com.acme.ecommerce.service.ProductService;
import com.acme.ecommerce.service.PurchaseService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class CheckoutControllerTest {

	@Mock
	private MockHttpSession session;

	@Mock
	private ProductService productService;
	@Mock
	private PurchaseService purchaseService;
	@Mock
	private ShoppingCart sCart;
	@InjectMocks
	private CheckoutController checkoutController;

	private MockMvc mockMvc;

	private static final BigDecimal ERROR = new BigDecimal(1.792);

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(checkoutController).build();
	}

	@Test
	public void checkoutCoupon_returnsCouponFieldAlongWithSubtotalInfo() throws Exception {
		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);

		when(sCart.getPurchase()).thenReturn(purchase);

		BigDecimal subTotal = new BigDecimal(1.99);

		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/coupon")).andDo(print()).andExpect(status().isOk())
				.andExpect(view().name("checkout_1"))
				.andExpect(model().attribute("subTotal", subTotal));
	}

	@Test
	public void checkoutCoupon_accessingCouponCodePageWithNoExistentPurchaseReturnsErrorPage() throws Exception {
		when(sCart.getPurchase()).thenReturn(null);
		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/coupon")).andDo(print())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/view-cart-error"));
	}

	@Test
	public void postCouponCode_redirectToShippingPageIfCouponCodeValid() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/coupon").param("code", "abcdf")).andDo(print())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("shipping"));
	}

	@Test
	public void postCouponCode_redirectToCouponPageIfCouponCodeInvalidLessThanFiveCharacters() throws Exception{
		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/coupon").param("code", "abcd")).andDo(print())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("coupon"));
	}

	@Test
	public void postCouponCode_redirectToCouponPageIfCouponCodeInvalidMoreThanTenCharacters() throws Exception{
		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/coupon").param("code", "abcdefghijk")).andDo(print())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("coupon"));
	}

	@Test
	public void checkoutShipping_returnsShippingInfoPageAlongWithReducedByCouponCodeSubTotal() throws Exception {
		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);
		when(sCart.getPurchase()).thenReturn(purchase);

		CouponCode coupon = new CouponCode();
		coupon.setCode("abcd");
		when(sCart.getCouponCode()).thenReturn(coupon);

		BigDecimal subTotal = new BigDecimal(1.99 * 0.9);

		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/shipping")).andDo(print())
			.andExpect(status().isOk())
			.andExpect(view().name("checkout_2"))
		.andExpect(model().attribute("subTotal", closeTo(subTotal, ERROR)));
	}

	@Test
	public void checkoutShipping_tryingToAccessShippingInfoWithoutExistingPurchasesReturnsErrorPage() throws Exception {
		when(sCart.getPurchase()).thenReturn(null);
		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/shipping")).andDo(print())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/view-cart-error"));
	}

	@Test
	public void postShipping_submittingValidInformationFromShippingFieldsRedirectsToBillingInfoPage() throws Exception {

		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);
		when(sCart.getPurchase()).thenReturn(purchase);

		CouponCode coupon = new CouponCode();
		coupon.setCode("abcd");
		when(sCart.getCouponCode()).thenReturn(coupon);

		when(purchaseService.save(purchase)).thenReturn(purchase);

		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/shipping").param("firstName", "john")
				.param("lastName", "smith").param("streetAddress", "123 main st.").param("city", "centerville")
				.param("state", "WA").param("zipCode", "12345").param("country", "USA")
				.param("phoneNumber", "1234567890").param("email", "ab@c.com")).andDo(print())
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("billing"));
	}

	@Test
	public void postShipping_submittingInvalidInformationFromShippingFieldsRedirectsToTheSamePageAndShowsValidationErrors() throws Exception {

		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);
		when(sCart.getPurchase()).thenReturn(purchase);

		CouponCode coupon = new CouponCode();
		coupon.setCode("abcd");
		when(sCart.getCouponCode()).thenReturn(coupon);

		when(purchaseService.save(purchase)).thenReturn(purchase);

		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/shipping")).andDo(print())
				.andExpect(flash().attribute("org.springframework.validation.BindingResult.shippingAddress",
						hasProperty("fieldErrorCount", equalTo(9))))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("shipping"));
	}

	@Test
	public void postShipping_submittingShippingInformationWhileNoPurchaseExistInSessionReturnsErrorPage() throws Exception {
		when(sCart.getPurchase()).thenReturn(null);
		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/shipping").param("firstName", "john")
				.param("lastName", "smith").param("streetAddress", "123 main st.").param("city", "centerville")
				.param("state", "WA").param("zipCode", "12345").param("country", "USA")
				.param("phoneNumber", "1234567890").param("email", "ab@c.com")).andDo(print())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/view-cart-error"));
	}

	@Test
	public void checkoutBilling_returnsBillingInformationFieldsSuccessfullyAlongWithSubtotal() throws Exception {
		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);
		when(sCart.getPurchase()).thenReturn(purchase);

		CouponCode coupon = new CouponCode();
		coupon.setCode("abcd");
		when(sCart.getCouponCode()).thenReturn(coupon);

		when(purchaseService.save(purchase)).thenReturn(purchase);

		BigDecimal subTotal = new BigDecimal(1.99 * 0.9);

		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/billing")).andDo(print())
			.andExpect(status().isOk())
			.andExpect(view().name("checkout_3"))
			.andExpect(model().attribute("subTotal", closeTo(subTotal, ERROR)));
	}

	@Test
	public void checkoutBilling_tryingToAccessBillingInformationWhileNoPurchaseExistInCartReturnsErrorPage() throws Exception {
		when(sCart.getPurchase()).thenReturn(null);
		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/billing")).andDo(print())
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/view-cart-error"));
	}

	@Test
	public void postBilling_submittingValidBillingInformationRedirectsToConfirmationPage() throws Exception {
		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);
		when(sCart.getPurchase()).thenReturn(purchase);

		CouponCode coupon = new CouponCode();
		coupon.setCode("abcd");
		when(sCart.getCouponCode()).thenReturn(coupon);

		when(purchaseService.save(purchase)).thenReturn(purchase);

		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/billing").param("firstName", "john")
				.param("lastName", "smith").param("streetAddress", "123 main st.").param("city", "centerville")
				.param("state", "WA").param("zipCode", "12345").param("country", "USA")
				.param("phoneNumber", "1234567890").param("email", "ab@c.com")
				.param("creditCardNumber", "1234567890123456").param("creditCardName", "john smith")
				.param("creditCardExpMonth", "5").param("creditCardExpYear", "2016").param("creditCardCVC", "123")
				.param("billingAddressSame", "false")).andDo(print())
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("confirmation"));
	}

	@Test
	public void postBilling_submittingInvalidBillingInformationRedirectsToSamePageAndShowsValidationErrors() throws Exception {
		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);
		when(sCart.getPurchase()).thenReturn(purchase);

		CouponCode coupon = new CouponCode();
		coupon.setCode("abcd");
		when(sCart.getCouponCode()).thenReturn(coupon);

		when(purchaseService.save(purchase)).thenReturn(purchase);
		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/billing")).andDo(print())
				.andExpect(flash().attribute("org.springframework.validation.BindingResult.billingObject",
						hasProperty("fieldErrorCount", equalTo(14))))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("billing"));
	}

	@Test
	public void postBilling_submittingBillingInformationWhileNoPurchasesExistInCartReturnsErrorPage() throws Exception {
		when(sCart.getPurchase()).thenReturn(null);
		mockMvc.perform(MockMvcRequestBuilders.post("/checkout/billing").param("firstName", "john")
				.param("lastName", "smith").param("streetAddress", "123 main st.").param("city", "centerville")
				.param("state", "WA").param("zipCode", "12345").param("country", "USA")
				.param("phoneNumber", "1234567890").param("email", "ab@c.com")
				.param("creditCardNumber", "1234567890123456").param("creditCardName", "john smith")
				.param("creditCardExpMonth", "5").param("creditCardExpYear", "2016").param("creditCardCVC", "123")
				.param("billingAddressSame", "false")).andDo(print())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/view-cart-error"));
	}

	@Test
	public void checkoutConfirmation_returnsOrderConfirmationInformationSuccessfully() throws Exception {
		Product product = productBuilder();

		when(productService.findById(1L)).thenReturn(product);

		Purchase purchase = purchaseBuilder(product);
		purchase.setCreditCardNumber("123456789101112");
		when(sCart.getPurchase()).thenReturn(purchase);

		CouponCode coupon = new CouponCode();
		coupon.setCode("abcd");
		when(sCart.getCouponCode()).thenReturn(coupon);

		when(purchaseService.save(purchase)).thenReturn(purchase);

		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/confirmation")).andDo(print())
			.andExpect(status().isOk())
			.andExpect(view().name("order_confirmation"));
	}

	@Test
	public void checkoutConfirmation_accessingOrderConfirmationInformationWhileNoPurchasesExistInCartReturnsErrorPage() throws Exception {
		when(sCart.getPurchase()).thenReturn(null);
		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/confirmation")).andDo(print())
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/view-cart-error"));
	}

	@Test
	public void getFile_successfulltDownloadsTheOrderConfirmationEmailReceipt() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/checkout/email")).andDo(print()).andExpect(status().isOk());
	}

	private Product productBuilder() {
		Product product = new Product();
		product.setId(1L);
		product.setDesc("TestDesc");
		product.setName("TestName");
		product.setPrice(new BigDecimal(1.99));
		product.setQuantity(3);
		product.setFullImageName("imagename");
		product.setThumbImageName("imagename");
		return product;
	}
	
	private Purchase purchaseBuilder(Product product) {
		ProductPurchase pp = new ProductPurchase();
		pp.setProductPurchaseId(1L);
		pp.setQuantity(1);
		pp.setProduct(product);
		List<ProductPurchase> ppList = new ArrayList<ProductPurchase>();
		ppList.add(pp);

		Purchase purchase = new Purchase();
		purchase.setId(1L);
		purchase.setProductPurchases(ppList);
		return purchase;
	}
}
