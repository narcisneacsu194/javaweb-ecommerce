package com.acme.ecommerce.controller;

import com.acme.ecommerce.FlashMessage;
import com.acme.ecommerce.domain.*;
import com.acme.ecommerce.service.PurchaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static com.acme.ecommerce.controller.WebConstants.*;


@Controller
@RequestMapping("/checkout")
@Scope("request")
public class CheckoutController {
	final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
	
	@Autowired 
    private TemplateEngine templateEngine;
	
	@Autowired
	private ShoppingCart sCart;
	
	@Autowired
	PurchaseService purchaseService;
	
	@RequestMapping("/coupon")
	String checkoutCoupon(Model model) {
    	Purchase purchase = sCart.getPurchase();
    	BigDecimal subTotal;
    	CouponCode couponCode = sCart.getCouponCode();

    	if (purchase != null) {
    		
    		if (couponCode == null) {
    			couponCode = new CouponCode();
    		}
    		
    		subTotal = computeSubtotal(purchase, couponCode);

			model.addAttribute("purchase", purchase);
    		model.addAttribute("subTotal", subTotal);
    		model.addAttribute("couponCode", couponCode);
    	} else {
    		logger.error("No purchases Found!");
    		return("redirect:/view-cart-error");
    	}
		return "checkout_1";
	}

	@RequestMapping(path="/coupon", method = RequestMethod.POST)
	String postCouponCode(@ModelAttribute(value="couponCode") @Valid CouponCode couponCode, BindingResult result,
						  RedirectAttributes redirectAttributes) {
		if(result.hasErrors()){
			redirectAttributes.addFlashAttribute("flash",
					new FlashMessage("The coupon code you entered is invalid. Try again!",
							FlashMessage.Status.FAILURE));
			return "redirect:coupon";
		}
    	sCart.setCouponCode(couponCode);
   	
		return "redirect:shipping";
	}
	
	@RequestMapping(path="/shipping", method=RequestMethod.GET)
	String checkoutShipping(Model model) {
    	Purchase purchase = sCart.getPurchase();
    	BigDecimal subTotal;
    	CouponCode couponCode = sCart.getCouponCode();
    	
    	model.addAttribute("purchase", purchase);
    	if (purchase != null) {
    		subTotal = computeSubtotal(purchase, couponCode);
    		
    		if (!model.containsAttribute("shippingAddress")) {
	    		Address modelAddress;
	    		if (purchase.getShippingAddress() != null) {
	    			modelAddress = purchase.getShippingAddress();
	    		} else {
	    			modelAddress = new Address();
	    		}
	    		model.addAttribute("shippingAddress", modelAddress);
    		}
    		model.addAttribute("subTotal", subTotal);
    		model.addAttribute("LIST_COUNTRIES", LIST_COUNTRIES);
    		model.addAttribute("LIST_STATES", LIST_STATES);
    	} else {
    		logger.error("No purchases Found!");
    		return("redirect:/view-cart-error");
    	}
		return "checkout_2";
	}
	
	@RequestMapping(path="/shipping", method = RequestMethod.POST)
	String postShipping(@ModelAttribute(value="shippingAddress") @Valid Address shippingAddress, final BindingResult result, RedirectAttributes redirectAttributes) {

    	if(result.hasErrors()) {
    		logger.error("Errors on fields: " + result.getFieldErrorCount());
    		redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.shippingAddress", result);
    		redirectAttributes.addFlashAttribute("shippingAddress", shippingAddress);
    		return "redirect:shipping";
		} else {
	    	Purchase purchase = sCart.getPurchase();
			if (purchase != null) {
				purchase.setShippingAddress(shippingAddress);
				sCart.setPurchase(purchaseService.save(purchase));
			} else {
	    		logger.error("No purchases Found!");
	    		return("redirect:/view-cart-error");
	    	}
		}
   	
		return "redirect:billing";
	}
	
	@RequestMapping("/billing")
	String checkoutBilling(Model model) {
    	Purchase purchase = sCart.getPurchase();
    	BigDecimal subTotal;
    	BigDecimal shippingCost;
    	CouponCode couponCode = sCart.getCouponCode();
    	
    	model.addAttribute("purchase", purchase);
    	if (purchase != null) {
    		
    		if (!model.containsAttribute("billingObject")) {
	    		CombinedBilling combinedBilling = new CombinedBilling();
	    		if (purchase.getBillingAddress() != null) {
	    			combinedBilling.setBillingAddressSame(purchase.isBillingAddressSame());
	    			combinedBilling.setFirstName(purchase.getBillingAddress().getFirstName());
	    			combinedBilling.setLastName(purchase.getBillingAddress().getLastName());
	    			combinedBilling.setStreetAddress(purchase.getBillingAddress().getStreetAddress());
	    			combinedBilling.setCity(purchase.getBillingAddress().getCity());
	    			combinedBilling.setState(purchase.getBillingAddress().getState());
	    			combinedBilling.setZipCode(purchase.getBillingAddress().getZipCode());
	    		}
	    		model.addAttribute("billingObject", combinedBilling);
    		}
    		
    		subTotal = computeSubtotal(purchase, couponCode);
    		shippingCost = computeShippingCost(purchase);
    		
    		BigDecimal orderTotal = subTotal.add(shippingCost);

    		model.addAttribute("shippingAddress", purchase.getShippingAddress());

    		model.addAttribute("subTotal", subTotal);
    		model.addAttribute("shippingCost", shippingCost);
    		model.addAttribute("orderTotal", orderTotal);
    		model.addAttribute("LIST_COUNTRIES", LIST_COUNTRIES);
    		model.addAttribute("LIST_STATES", LIST_STATES);
    		model.addAttribute("LIST_MONTHS", LIST_MONTHS);
    		model.addAttribute("LIST_YEARS", LIST_YEARS);
    	} else {
    		logger.error("No purchases Found!");
    		return("redirect:/view-cart-error");
    	}
		return "checkout_3";
	}
	
	@RequestMapping(path="/billing", method = RequestMethod.POST)
	String postBilling(@ModelAttribute(value="billingObject") @Valid CombinedBilling combinedBilling, 
			 final BindingResult result, RedirectAttributes redirectAttributes) {

		if(result.hasErrors()) {
    		redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.billingObject", result);
    		redirectAttributes.addFlashAttribute("billingObject", combinedBilling);
    		return "redirect:billing";
		} else {
			Purchase purchase = sCart.getPurchase();
			if (purchase != null) {
				if (!combinedBilling.isBillingAddressSame()) {
					Address billingAddress = new Address();
					billingAddress.setFirstName(combinedBilling.getFirstName());
					billingAddress.setLastName(combinedBilling.getLastName());
					billingAddress.setStreetAddress(combinedBilling.getStreetAddress());
					billingAddress.setCity(combinedBilling.getCity());
					billingAddress.setState(combinedBilling.getState());
					billingAddress.setZipCode(combinedBilling.getZipCode());
					billingAddress.setPhoneNumber(combinedBilling.getPhoneNumber());
					billingAddress.setEmail(combinedBilling.getEmail());
					billingAddress.setCountry(combinedBilling.getCountry());
					purchase.setBillingAddress(billingAddress);
				} else {
					if (purchase.getBillingAddress() != null) {
						purchase.setBillingAddress(null);
					}
				}
				purchase.setCreditCardNumber(combinedBilling.getCreditCardNumber());
				purchase.setCreditCardName(combinedBilling.getCreditCardName());
				purchase.setCreditCardExpMonth(combinedBilling.getCreditCardExpMonth());
				purchase.setCreditCardExpYear(combinedBilling.getCreditCardExpYear());
				purchase.setCreditCardCVC(combinedBilling.getCreditCardCVC());
				purchase.setOrderNumber(String.valueOf(ThreadLocalRandom.current().nextInt(1000000, 9999999)));
				sCart.setPurchase(purchaseService.save(purchase));
			} else {
	    		logger.error("No purchases Found!");
	    		return("redirect:/view-cart-error");
	    	}
		}
   	
		return "redirect:confirmation";
	}
	
	@RequestMapping("/confirmation")
	String checkoutConfirmation(Model model) {
    	Purchase purchase = sCart.getPurchase();
    	BigDecimal subTotal;
       	BigDecimal shippingCost;
    	CouponCode couponCode = sCart.getCouponCode();
    	
    	model.addAttribute("purchase", purchase);
    	if (purchase != null) {
    		subTotal = computeSubtotal(purchase, couponCode);
    		shippingCost = computeShippingCost(purchase);
    		BigDecimal orderTotal = subTotal.add(shippingCost);

    		model.addAttribute("subTotal", subTotal);
    		model.addAttribute("shippingCost", shippingCost);
    		model.addAttribute("orderTotal", orderTotal);
    		
    		model.addAttribute("orderNumber", purchase.getOrderNumber());
    		model.addAttribute("shippingAddress", purchase.getShippingAddress());
    		model.addAttribute("billingAddress", purchase.getBillingAddress());
    		model.addAttribute("creditCard", getHiddenCreditCardNumber(purchase.getCreditCardNumber()));
    	} else {
    		logger.error("No purchases Found!");
    		return("redirect:/view-cart-error");
    	}
    	
		return "order_confirmation";
	}

	private String getHiddenCreditCardNumber(String unhiddenCreditCardNumber){
		Integer creditCardNumberLengthMinusFour = unhiddenCreditCardNumber.length() - 4;
		String creditCardNumberHidden = "";
		String creditCardLastFourDigits = unhiddenCreditCardNumber.substring(creditCardNumberLengthMinusFour, unhiddenCreditCardNumber.length());
		for(int i = 0;i < creditCardNumberLengthMinusFour;i++){
			creditCardNumberHidden = creditCardNumberHidden.concat("*");
		}
		creditCardNumberHidden += creditCardLastFourDigits;

		return creditCardNumberHidden;
	}
	
	@RequestMapping(value = "/email", method = RequestMethod.GET)
	public void getFile(HttpServletResponse response) {
		try {
	        final Context ctx = new Context();

	    	Purchase purchase = sCart.getPurchase();
	    	
	    	ctx.setVariable("purchase", purchase);
	    	if (purchase != null) {
	    		
	    		ctx.setVariable("orderNumber", purchase.getOrderNumber());
	    		ctx.setVariable("shippingAddress", purchase.getShippingAddress());
	    		
	    		final String htmlContent = this.templateEngine.process("email_confirmation", ctx);
			
		    	response.setHeader("Content-Disposition", "attachment; filename=email_receipt.html");
		    	
		    	response.setContentType("text/html");
		    	
		    	InputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(htmlContent.getBytes()));
		    	
		    	FileCopyUtils.copy(inputStream, response.getOutputStream());
		    	
		    	response.flushBuffer();
	    	} else {
	    		logger.error("No purchases Found!");
	    	}

	    	sCart.setCouponCode(null);
	    	sCart.setPurchase(null);
	    } catch (IOException ex) {
	      logger.info("Error writing file to output stream. Filename was '{}'", "email_receipt.html", ex);
	      throw new RuntimeException("IOError writing file to output stream");
	    }
	}
	
	private BigDecimal computeSubtotal(Purchase purchase, CouponCode couponCode) {
		
		BigDecimal subTotal = new BigDecimal(0);
		
		for (ProductPurchase pp : purchase.getProductPurchases()) {
			logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName() + " at " + "$" + pp.getProduct().getPrice());
			subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
		}
		
		if (couponCode.getCode() != null && !couponCode.getCode().isEmpty()) {
			logger.info("Applying discount for coupon");
			subTotal = subTotal.multiply(new BigDecimal(0.9));
		}
		
		return subTotal;
	}
	
	private BigDecimal computeShippingCost(Purchase purchase) {
		BigDecimal shippingCost = new BigDecimal(0);
		
		for (ProductPurchase pp : purchase.getProductPurchases()) {
			BigDecimal itemShippingCost = new BigDecimal(0).add(COST_PER_ITEM.multiply(new BigDecimal(pp.getQuantity())));
			logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName() + " shipping cost of " + itemShippingCost);
			shippingCost = shippingCost.add(itemShippingCost);
		}
		
		return shippingCost;
	}
}
