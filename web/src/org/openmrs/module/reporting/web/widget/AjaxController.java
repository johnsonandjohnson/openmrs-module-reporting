package org.openmrs.module.reporting.web.widget;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.ConceptWord;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.evaluation.EvaluationUtil;
import org.openmrs.module.evaluation.parameter.Mapped;
import org.openmrs.module.evaluation.parameter.Parameter;
import org.openmrs.module.evaluation.parameter.Parameterizable;
import org.openmrs.module.util.ParameterizableUtil;
import org.openmrs.module.util.ReflectionUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AjaxController {

	protected static Log log = LogFactory.getLog(AjaxController.class);
	
	/**
	 * Default Constructor
	 */
	public AjaxController() { }

    /**
     * Concept Search
     */
    @RequestMapping("/module/reporting/widget/conceptSearch")
    public void conceptSearch(ModelMap model, HttpServletRequest request, HttpServletResponse response, 
		    		@RequestParam(required=true, value="q") String query) throws Exception {
    	
    	response.setContentType("text/plain");
    	ServletOutputStream out = response.getOutputStream();
    	List<Locale> l = new Vector<Locale>();
    	l.add(Context.getLocale());
    	List<ConceptWord> words = Context.getConceptService().getConceptWords(query, l, false, null, null, null, null, null, null, null);
    	for (Iterator<ConceptWord> i = words.iterator(); i.hasNext();) {
    		ConceptWord w = i.next();
    		String ds = w.getConcept().getDisplayString();
    		if (w.getConceptName().isPreferred() || w.getConceptName().getName().equalsIgnoreCase(ds)) {
    			out.print(w.getConceptName().getName());
    		}
    		else {
    			out.print( w.getConcept().getDisplayString() + " (" + w.getConceptName().getName() + ")");
    		}
    		out.print("|" + w.getConcept().getUuid() + (i.hasNext() ? "\n" : ""));
    	}
    }
    
    /**
     * Concept Search
     */
    @RequestMapping("/module/reporting/widget/userSearch")
    public void userSearch(ModelMap model, HttpServletRequest request, HttpServletResponse response, 
    				@RequestParam(required=false, value="roles") String roles,
		    		@RequestParam(required=true, value="q") String query) throws Exception {
    	
    	response.setContentType("text/plain");
    	ServletOutputStream out = response.getOutputStream();
    	
		List<Role> roleList = null;
		if (StringUtils.isNotEmpty(roles)) {
			roleList = new ArrayList<Role>();
			for (String roleName : roles.split(",")) {
				roleList.add(Context.getUserService().getRole(roleName));
			}
			
		}
		for (Iterator<User> i = Context.getUserService().getUsers(query, roleList, false).iterator(); i.hasNext();) {
			User u = i.next();
			out.print(u.getFamilyName() + ", " + u.getGivenName() + "|" + u.getUuid() + (i.hasNext() ? "\n" : ""));
		}
    }
    
    /**
     * Portlet Loading
     */
    @RequestMapping("/module/reporting/widget/mappedProperty")
    public void loadWidget(ModelMap model, HttpServletRequest request, HttpServletResponse response, 
		    		@RequestParam(required=true, value="id") String id,
		    		@RequestParam(required=true, value="type") Class<? extends Parameterizable> type,
		    		@RequestParam(required=true, value="property") String property,
		    		@RequestParam(required=false, value="currentKey") String currentKey,
		    		@RequestParam(required=false, value="uuid") String uuid,
		    		@RequestParam(required=false, value="mappedUuid") String mappedUuid) throws Exception {
    	
    	response.setContentType("text/html");
    	ServletOutputStream out = response.getOutputStream();
    	
    	// Get parent if uuid supplied
    	Parameterizable parent = null;
    	if (StringUtils.isNotEmpty(uuid)) {
    		parent = ParameterizableUtil.getParameterizable(uuid, type);
    	}

		// Get generic type of the Mapped property
		Field f = ReflectionUtil.getField(type, property);
		Class<?> fieldType = ReflectionUtil.getFieldType(f);
		boolean isList = List.class.isAssignableFrom(fieldType);
		boolean isMap = Map.class.isAssignableFrom(fieldType);
		
		Class<? extends Parameterizable> mappedType = ParameterizableUtil.getMappedType(type, property);
       	Parameterizable mappedChild = null;
       	Map<String, Object> mappings = new HashMap<String, Object>();

		if (StringUtils.isNotEmpty(uuid)) {
	       	if (StringUtils.isEmpty(mappedUuid)) {
	       		Mapped<Parameterizable> mapped = ParameterizableUtil.getMappedProperty(parent, property, currentKey);
	       		if (mapped != null) {
	       			mappedChild = mapped.getParameterizable();
	       			mappings = mapped.getParameterMappings();
	       		}
	       	}
	       	else if (mappedUuid != null) {
	       		mappedChild = ParameterizableUtil.getParameterizable(mappedUuid, mappedType);
	       	}
	       	
	       	Map<String, String> mappedParams = new HashMap<String, String>();
	       	Map<String, String> complexParams = new HashMap<String, String>();
	       	Map<String, String> fixedParams = new HashMap<String, String>();
	       	Map<String, Set<String>> allowedParams = new HashMap<String, Set<String>>();
       	
	       	if (mappedChild != null) {
				for (Parameter p : mappedChild.getParameters()) {
					Object mappedObjVal = mappings.get(p.getName());
					
					Set<String> allowed  = new HashSet<String>();
					for (Parameter parentParam : parent.getParameters()) {
						if (p.getType() == parentParam.getType()) {
							allowed.add(parentParam.getName());
						}
					}
					allowedParams.put(p.getName(), allowed);
					
					if (mappedObjVal != null && mappedObjVal instanceof String) {
						String mappedVal = (String) mappedObjVal;
						if (EvaluationUtil.isExpression(mappedVal)) {
							mappedVal = EvaluationUtil.stripExpression(mappedVal);
							if (parent.getParameter(mappedVal) != null) {
								mappedParams.put(p.getName(), mappedVal);
							}
							else {
								complexParams.put(p.getName(), mappedVal);
							}
						}
						else {
							fixedParams.put(p.getName(), mappedVal);
						}
					}
				}
	       	}
		}
		
		/*
		out.print(mappedType.getSimpleName() + ":" );
		Widget w = Widget

		<td><rpt:widget id="parameterizableSelector${model.id}" name="mappedUuid" type="${model.mappedType.name}" defaultValue="${model.mappedObj}"/></td>
	</tr>

		
		*/
		
    }
}
