/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.objects.classes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import qupath.lib.common.ColorTools;

/**
 * Factory for creating PathClasses.
 * <p>
 * This must be used in favor of creating PathClass objects directly to ensure that 
 * only one PathClass with a specified name (and, optionally, ancestry) can exist at any time.
 * 
 * @author Pete Bankhead
 *
 */
public class PathClassFactory {
	
	/**
	 * Enum representing standard classifications. Exists mostly to ensure consisting naming (including capitalization).
	 */
	public enum StandardPathClasses { 
		/**
		 * Tumor classification
		 */
		TUMOR,
		/**
		 * Stroma classification
		 */
		STROMA,
		/**
		 * Immune cell classification
		 */
		IMMUNE_CELLS,
		/**
		 * Ignore classification, indicating what should not be further measured (e.g. background, whitespace)
		 */
		IGNORE,
		/**
		 * Root object classification
		 */
		IMAGE_ROOT,
		/**
		 * Necrosis classification
		 */
		NECROSIS,
		/**
		 * Other classification
		 */
		OTHER,
		/**
		 * Region class. This behaves slightly differently from other classes, e.g. it is not filled in when applied to
		 * annotations.  Consequently it is good to heavily annotated regions, or possibly detected tissue 
		 * containing further annotations inside.
		 */
		REGION;
		
		
		PathClass getPathClass() {
			switch (this) {
			case IGNORE:
				return PathClassFactory.getPathClass("Ignore*", ColorTools.makeRGB(180, 180, 180));
			case IMAGE_ROOT:
				return PathClassFactory.getPathClass("Image", ColorTools.makeRGB(128, 128, 128));
			case IMMUNE_CELLS:
				return PathClassFactory.getPathClass("Immune cells", ColorTools.makeRGB(160, 90, 160));
			case NECROSIS:
				return PathClassFactory.getPathClass("Necrosis", ColorTools.makeRGB(50, 50, 50));
			case OTHER:
				return PathClassFactory.getPathClass("Other", ColorTools.makeRGB(255, 200, 0));
			case REGION:
				return PathClassFactory.getPathClass("Region*", ColorTools.makeRGB(0, 0, 180));
			case STROMA:
				return PathClassFactory.getPathClass("Stroma", ColorTools.makeRGB(150, 200, 150));
			case TUMOR:
				return PathClassFactory.getPathClass("Tumor", ColorTools.makeRGB(200, 0, 0));
			default:
				throw new IllegalArgumentException("Unknown value!");
			}
		}
		
	}

	private static Map<String, PathClass> mapPathClasses = new HashMap<>();

	private final static PathClass NULL_CLASS = new PathClass();
	
	final static String POSITIVE = "Positive";
	final static String NEGATIVE = "Negative";
	final static String ONE_PLUS = "1+";
	final static String TWO_PLUS = "2+";
	final static String THREE_PLUS = "3+";
	static List<String> intensityClassNames = Arrays.asList(ONE_PLUS, TWO_PLUS, THREE_PLUS);
	
	private static final Integer COLOR_POSITIVE = ColorTools.makeRGB(200, 50, 50);
	private static final Integer COLOR_NEGATIVE = ColorTools.makeRGB(90, 90, 180);
	private static final Integer COLOR_ONE_PLUS = ColorTools.makeRGB(255, 215, 0);
	private static final Integer COLOR_TWO_PLUS = ColorTools.makeRGB(225, 150, 50);
	private static final Integer COLOR_THREE_PLUS = ColorTools.makeRGB(200, 50, 50);
	
	
	
	static boolean classExists(String classString) {
		return mapPathClasses.containsKey(classString);
	}
	
	
	/**
	 * Validate a non-null name, throwing an IllegalArgumentException if the name contains invalid characters.
	 * @param name
	 */
	private static void validateName(String name) {
		if (name.contains(":") || name.contains("\n"))
			throw new IllegalArgumentException("PathClass names cannot contain new line or colon (:) characters!");
	}
	
	/**
	 * Get the PathClass object associated with a specific name.  Note that this name must not contain newline or 
	 * colon characters; doing so will result in an IllegalArgumentException being thrown.
	 * 
	 * @param name
	 * @param rgb
	 * @return
	 */
	public static PathClass getPathClass(String name, Integer rgb) {
		if (name == null || name.equals(NULL_CLASS.toString()) || name.equals(NULL_CLASS.getName()))
			return NULL_CLASS;
		
		// Handle requests for derived classes
		var split = name.split(":");
		if (split.length > 1) {
			var pathClass = getPathClass(split[0], rgb);
			for (int i = 1; i < split.length; i++) {
				var temp = split[i].trim();
				if (!temp.isBlank())
					pathClass = getDerivedPathClass(pathClass, temp, rgb);
			}
			return pathClass;
		}
		
		validateName(name);
		
		synchronized (mapPathClasses) {
			PathClass pathClass = mapPathClasses.get(name);
			if (pathClass == null) {
				if (rgb == null) {
					// Use default colors for intensity classes
					if (name.equals(ONE_PLUS)) {
						rgb = ColorTools.makeScaledRGB(COLOR_ONE_PLUS, 1.25);
					} else if (name.equals(TWO_PLUS)) {
						rgb = ColorTools.makeScaledRGB(COLOR_TWO_PLUS, 1.25);
					} else if (name.equals(THREE_PLUS))
						rgb = ColorTools.makeScaledRGB(COLOR_THREE_PLUS, 1.25);
					else if (name.equals(POSITIVE)) {
						rgb = ColorTools.makeScaledRGB(COLOR_POSITIVE, 1.25);
					} else if (name.equals(NEGATIVE)) {
						rgb = ColorTools.makeScaledRGB(COLOR_NEGATIVE, 1.25);
					} else {
						// Create a random color
						// Use the hashcode of the String as a seed - so that the same 
						// color is generated reproducibly for the same name.
						Random random = new Random(name.hashCode());
						rgb = ColorTools.makeRGB(
								random.nextInt(256),
								random.nextInt(256),
								random.nextInt(256));
					}
				}
				pathClass = new PathClass(null, name, rgb);
				mapPathClasses.put(pathClass.toString(), pathClass);
			}
			return pathClass;
		}
	}
	
	/**
	 * Get the PathClass object associated with a specific name, using the default color.  
	 * Note that this name must not contain newline or colon characters; doing so will 
	 * result in an IllegalArgumentException being thrown.
	 * 
	 * @param name
	 * @return
	 * 
	 * @see #getPathClass(String, Integer)
	 */
	public static PathClass getPathClass(String name) {
		return getPathClass(name, (Integer)null);
	}
	
	
	/**
	 * Return a singleton version of a specific PathClass.  This is useful during deserialization, which can 
	 * circumvent the 'right' way to request PathClass objects via static methods.
	 * 
	 * @param pathClass
	 * @return
	 */
	public static PathClass getSingletonPathClass(PathClass pathClass) {
		if (pathClass.getParentClass() == null)
			return getPathClass(pathClass.getName(), pathClass.getColor());
		return getDerivedPathClass(getSingletonPathClass(pathClass.getParentClass()), pathClass.getName(), pathClass.getColor());
	}
	
	/**
	 * Get a PathClass that has been derived from a parent class.
	 * 
	 * @param parentClass
	 * @param name
	 * @param rgb
	 * @return
	 */
	public static PathClass getDerivedPathClass(PathClass parentClass, String name, Integer rgb) {
		if (parentClass == null || !parentClass.isValid())
			return getPathClass(name, rgb);
		String nameNew = PathClass.derivedClassToString(parentClass, name);
//		mapPathDerivedClasses.clear();
		PathClass pathClass = mapPathClasses.get(nameNew);
		if (pathClass == null) {
			if (rgb == null) {
				boolean isTumor = getPathClass(StandardPathClasses.TUMOR) == parentClass;
				int parentRGB = parentClass.getColor();
				if (name.equals(ONE_PLUS)) {
					rgb = isTumor ? COLOR_ONE_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.9);
				} else if (name.equals(TWO_PLUS)) {
					rgb = isTumor ? COLOR_TWO_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.6);
				} else if (name.equals(THREE_PLUS))
					rgb = isTumor ? COLOR_THREE_PLUS : ColorTools.makeScaledRGB(parentRGB, 0.4);
				else if (name.equals(POSITIVE)) {
					rgb = isTumor ? COLOR_POSITIVE : ColorTools.makeScaledRGB(parentRGB, 0.75);
				} else if (name.equals(NEGATIVE)) {
					rgb = isTumor ? COLOR_NEGATIVE : ColorTools.makeScaledRGB(parentRGB, 1.25);
				} else {
					double scale = 1.5;
					rgb = ColorTools.makeScaledRGB(parentRGB, scale);
				}
			}
//				rgb = new Color(parentClass.getColor()).brighter().getRGB();
			pathClass = new PathClass(parentClass, name, rgb);
			mapPathClasses.put(pathClass.toString(), pathClass);
		}
		return pathClass;
	}
	
	/**
	 * Get a standalone or derived 1+ classification, indicating weak positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getOnePlus(PathClass parentClass) {
		return getDerivedPathClass(parentClass, ONE_PLUS, null);
	}

	/**
	 * Get a standalone or derived 2+ classification, indicating moderate positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getTwoPlus(PathClass parentClass) {
		return getDerivedPathClass(parentClass, TWO_PLUS, null);
	}

	/**
	 * Get a standalone or derived 3+ classification, indicating strong positivity
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getThreePlus(PathClass parentClass) {
		return getDerivedPathClass(parentClass, THREE_PLUS, null);
	}
	
	/**
	 * Get a standalone or derived Negative classification
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getNegative(PathClass parentClass) {
		return getDerivedPathClass(parentClass, NEGATIVE, null);
	}
	
	/**
	 * Get a standalone or derived Positive classification
	 * @param parentClass parent classification (may be null)
	 * @return
	 */
	public static PathClass getPositive(PathClass parentClass) {
		return getDerivedPathClass(parentClass, POSITIVE, null);
	}
	
	/**
	 * Get a standard PathClass.
	 * @param pathClass
	 * @return
	 */
	public static PathClass getPathClass(StandardPathClasses pathClass) {
		return pathClass.getPathClass();
	}

	/**
	 * Return a special 'null' class to represent no classification.
	 * <p>
	 * This is useful for displaying available classes; <i>it should not be set as the class for any object</i>, 
	 * rather an object that is unclassified should have a classification of null.
	 * 
	 * @return
	 */
	public static PathClass getPathClassUnclassified() {
		return NULL_CLASS;
	}

}
