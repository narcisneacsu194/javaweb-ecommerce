package com.acme.ecommerce.controller;

import com.acme.ecommerce.FlashMessage;
import com.acme.ecommerce.domain.Product;
import com.acme.ecommerce.domain.ProductPurchase;
import com.acme.ecommerce.domain.Purchase;
import com.acme.ecommerce.domain.ShoppingCart;
import com.acme.ecommerce.service.ProductService;
import com.acme.ecommerce.service.PurchaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/cart")
@Scope("request")
public class CartController {
	final Logger logger = LoggerFactory.getLogger(CartController.class);
	
	@Autowired
	PurchaseService purchaseService;
	
	@Autowired
	private ProductService productService;
	
	@Autowired
	private ShoppingCart sCart;
	
	@Autowired
	private HttpSession session;
	
    @RequestMapping("")
    public String viewCart(Model model) {
    	logger.debug("Getting Product List");
    	logger.debug("Session ID = " + session.getId());
    	
    	Purchase purchase = sCart.getPurchase();
    	BigDecimal subTotal = new BigDecimal(0);

    	if (purchase != null) {
    		for (ProductPurchase pp : purchase.getProductPurchases()) {
    			logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName());
    			subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
    		}

			model.addAttribute("purchase", purchase);
    		model.addAttribute("subTotal", subTotal);
    	} else {
    		logger.error("No purchases Found for session ID=" + session.getId());
    		return "redirect:/view-cart-error";
    	}
        return "cart";
    }
    
    @RequestMapping(path="/add", method = RequestMethod.POST)
    public RedirectView addToCart(@ModelAttribute(value="productId") long productId, @ModelAttribute(value="quantity") int quantity,
								  RedirectAttributes redirectAttributes) {
    	boolean productAlreadyInCart = false;
    	RedirectView redirect = new RedirectView("/product/");
		redirect.setExposeModelAttributes(false);
    	
    	Product addProduct = productService.findById(productId);
		if (addProduct != null) {

			if(addProduct.getQuantity() < quantity){
				redirect = new RedirectView(String.format("/product/detail/%s", productId));
				redirect.setExposeModelAttributes(false);
				redirectAttributes.addFlashAttribute("flash", new FlashMessage(String.format("There are only %s %ss left in stock.", addProduct.getQuantity(),
						addProduct.getName()), FlashMessage.Status.FAILURE));
				return redirect;
			}

			addProduct.setQuantity(addProduct.getQuantity() - quantity);

			redirect.setExposeModelAttributes(false);

	    	logger.debug("Adding Product: " + addProduct.getId());
	    	
    		Purchase purchase = sCart.getPurchase();
    		if (purchase == null) {
    			purchase = new Purchase();
    			sCart.setPurchase(purchase);
    		} else {
    			for (ProductPurchase pp : purchase.getProductPurchases()) {
    				if (pp.getProduct() != null) {
    					if (pp.getProduct().getId().equals(productId)) {
    						pp.setQuantity(pp.getQuantity() + quantity);
    						productAlreadyInCart = true;
    						break;
    					}
    				}
    			}
    		}
    		if (!productAlreadyInCart) {
    			ProductPurchase newProductPurchase = new ProductPurchase();
    			newProductPurchase.setProduct(addProduct);
    			newProductPurchase.setQuantity(quantity);
    			newProductPurchase.setPurchase(purchase);
        		purchase.getProductPurchases().add(newProductPurchase);
    		}
    		logger.debug("Added " + quantity + " of " + addProduct.getName() + " to cart");
			redirectAttributes.addFlashAttribute("flash", new FlashMessage(String.format("Added %s of %s to cart.", quantity, addProduct.getName()),
					FlashMessage.Status.SUCCESS));
    		sCart.setPurchase(purchaseService.save(purchase));
		} else {
			logger.error("Attempt to add unknown product: " + productId);
			redirect.setUrl("/add-product-error");
		}

    	return redirect;
    }
 
    @RequestMapping(path="/update", method = RequestMethod.POST)
    public RedirectView updateCart(@ModelAttribute(value="productId") long productId, @ModelAttribute(value="newQuantity") int newQuantity,
								   RedirectAttributes redirectAttributes) {
    	logger.debug("Updating Product: " + productId + " with Quantity: " + newQuantity);
		RedirectView redirect = new RedirectView("/cart");
		redirect.setExposeModelAttributes(false);
    	
    	Product updateProduct = productService.findById(productId);
    	if (updateProduct != null) {

    		Purchase purchase = sCart.getPurchase();
    		if (purchase == null) {
    			logger.error("Unable to find shopping cart for update");
    			redirect.setUrl("/view-cart-error");
    		} else {
    			for (ProductPurchase pp : purchase.getProductPurchases()) {
    				if (pp.getProduct() != null) {
    					if (pp.getProduct().getId().equals(productId)) {
    						if (newQuantity > 0) {

								if(updateProduct.getQuantity() + pp.getQuantity() < newQuantity){
									redirectAttributes.addFlashAttribute("flash", new FlashMessage(String.format("You can update to a maximum of %s %ss.", updateProduct.getQuantity(),
											updateProduct.getName()), FlashMessage.Status.FAILURE));
									return redirect;
								}

								updateProduct.setQuantity(updateProduct.getQuantity() + pp.getQuantity() - newQuantity);
    							pp.setQuantity(newQuantity);
    							logger.debug("Updated " + updateProduct.getName() + " to " + newQuantity);
								redirectAttributes.addFlashAttribute("flash", new FlashMessage(String.format("Updated %s to %s.", updateProduct.getName(),
										newQuantity), FlashMessage.Status.SUCCESS));
    						} else {
    							purchase.getProductPurchases().remove(pp);
    							logger.debug("Removed " + updateProduct.getName() + " because quantity was set to " + newQuantity);
								redirectAttributes.addFlashAttribute("flash", new FlashMessage(String.format("Removed %s because quantity was set to %s.",
										updateProduct.getName(), newQuantity), FlashMessage.Status.SUCCESS));
    						}
    						break;
    					}
    				}
    			}
    		}
    		sCart.setPurchase(purchaseService.save(purchase));
    	} else {
    		logger.error("Attempt to update on non-existent product");
    		redirect.setUrl("/product-error");
    	}
    	
    	return redirect;
    }
    
    @RequestMapping(path="/remove", method = RequestMethod.POST)
    public RedirectView removeFromCart(@ModelAttribute(value="productId") long productId, RedirectAttributes redirectAttributes) {
    	logger.debug("Removing Product: " + productId);
		RedirectView redirect = new RedirectView("/cart");
		redirect.setExposeModelAttributes(false);
    	
    	Product updateProduct = productService.findById(productId);
    	if (updateProduct != null) {
    		Purchase purchase = sCart.getPurchase();
    		if (purchase != null) {
    			for (ProductPurchase pp : purchase.getProductPurchases()) {
    				if (pp.getProduct() != null) {
    					if (pp.getProduct().getId().equals(productId)) {
							updateProduct.setQuantity(updateProduct.getQuantity() + pp.getQuantity());
    						purchase.getProductPurchases().remove(pp);
   							logger.debug("Removed " + updateProduct.getName());
							redirectAttributes.addFlashAttribute("flash", new FlashMessage(String.format("Removed %s", updateProduct.getName()),
									FlashMessage.Status.SUCCESS));
    						break;
    					}
    				}
    			}
    			purchase = purchaseService.save(purchase);
    			sCart.setPurchase(purchase);
    			if (purchase.getProductPurchases().isEmpty()) {
        			redirect.setUrl("/product/");
        		}
    		} else {
    			logger.error("Unable to find shopping cart for update");
    			redirect.setUrl("/view-cart-error");
    		}
    	} else {
    		logger.error("Attempt to update on non-existent product");
    		redirect.setUrl("/product-error");
    	}

    	return redirect;
    }
    
    @RequestMapping(path="/empty", method = RequestMethod.POST)
    public RedirectView emptyCart(RedirectAttributes redirectAttributes) {
    	RedirectView redirect = new RedirectView("/product/");
		redirect.setExposeModelAttributes(false);
    	
    	logger.debug("Emptying Cart");
    	Purchase purchase = sCart.getPurchase();
		if (purchase != null) {
			for(ProductPurchase productPurchase : purchase.getProductPurchases()){
				Product product = productService.findById(productPurchase.getProduct().getId());
				product.setQuantity(product.getQuantity() + productPurchase.getQuantity());
			}
			purchase.getProductPurchases().clear();
			sCart.setPurchase(purchaseService.save(purchase));
			redirectAttributes.addFlashAttribute("flash", new FlashMessage("The cart has been completely emptied.", FlashMessage.Status.SUCCESS));
		} else {
			logger.error("Unable to find shopping cart for update");
			redirect.setUrl("/view-cart-error");
		}
		
    	return redirect;
    }

    @RequestMapping(path = "/view-cart-error")
	public String displayViewCartError(Model model){
		model.addAttribute("message", "There is no purchase available inside the cart.");
		return "error";
	}

	@RequestMapping(path = "/add-product-error")
	public String displayAddProductError(Model model){
		model.addAttribute("message", "The product you want to add to the cart doesn't exist.");
		return "error";
	}
}
