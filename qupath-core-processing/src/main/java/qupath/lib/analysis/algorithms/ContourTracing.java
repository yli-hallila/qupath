/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */


package qupath.lib.analysis.algorithms;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.PolygonExtracter;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * 
 * @author Pete Bankhead
 */
public class ContourTracing {
	
	private final static Logger logger = LoggerFactory.getLogger(ContourTracing.class);
	
	/**
	 * Create a traced ROI from a raster.
	 * 
	 * @param raster input raster
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param band band (channel) to threshold
	 * @param request region request used to translate and rescale to the image space, and determine the image plane
	 * @return a ROI created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 * 
	 * @see #createTracedGeometry(Raster, float, float, int, RegionRequest)
	 */
	public static ROI createTracedROI(Raster raster, float minThresholdInclusive, float maxThresholdInclusive, int band, RegionRequest request) {
		var geom = createTracedGeometry(raster, minThresholdInclusive, maxThresholdInclusive, band, request);
		return GeometryTools.geometryToROI(geom, request == null ? ImagePlane.getDefaultPlane() : request.getPlane());
	}
	
	/**
	 * Create a traced ROI from a {@link SimpleImage}.
	 * 
	 * @param image input image
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param request region request used to translate and rescale to the image space, and determine the image plane
	 * @return a ROI created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 * 
	 * @see #createTracedGeometry(SimpleImage, float, float, RegionRequest)
	 */
	public static ROI createTracedROI(SimpleImage image, float minThresholdInclusive, float maxThresholdInclusive, RegionRequest request) {
		var geom = createTracedGeometry(image, minThresholdInclusive, maxThresholdInclusive, request);
		return GeometryTools.geometryToROI(geom, request == null ? ImagePlane.getDefaultPlane() : request.getPlane());
	}
	
	/**
	 * Create a traced geometry from a {@link SimpleImage}.
	 * 
	 * @param image input image
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param request optional region request; if provided, the geometry will be translated and rescaled to the image space
	 * @return a polygonal geometry created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 */
	public static Geometry createTracedGeometry(SimpleImage image, float minThresholdInclusive, float maxThresholdInclusive, RegionRequest request) {
		
		// If we are translating but not rescaling, we can do this during tracing
		double xOffset = 0;
		double yOffset = 0;
		if (request != null && request.getDownsample() == 1) {
			xOffset = request.getX();
			yOffset = request.getY();
		}
		
		var geom = traceGeometry(image, minThresholdInclusive, maxThresholdInclusive, xOffset, yOffset);
		
		// Handle rescaling if needed
		if (request != null && request.getDownsample() != 1) {
			double scale = request.getDownsample();
			var transform = AffineTransformation.scaleInstance(scale, scale);
			transform = transform.translate(request.getX(), request.getY());
			if (!transform.isIdentity())
				geom = transform.transform(geom);
		}
		
		return geom;
	}
	
	
	/**
	 * Create a traced geometry from a raster.
	 * 
	 * @param raster input raster
	 * @param minThresholdInclusive minimum threshold value
	 * @param maxThresholdInclusive maximum threshold value
	 * @param band band (channel) to threshold
	 * @param request optional region request; if provided, the geometry will be translated and rescaled to the image space
	 * @return a polygonal geometry created by tracing pixel values &ge; minThresholdInclusive and &le; maxThresholdInclusive
	 */
	public static Geometry createTracedGeometry(Raster raster, float minThresholdInclusive, float maxThresholdInclusive, int band, RegionRequest request) {
		
		var pixels = raster.getSamples(0, 0, raster.getWidth(), raster.getHeight(), band, (float[])null);
		var image = SimpleImages.createFloatImage(pixels, raster.getWidth(), raster.getHeight());
		
		return createTracedGeometry(image, minThresholdInclusive, maxThresholdInclusive, request);
	}
	
	
	
	
	
	/**
	 * Helper class defining global thresholds to apply to a single image channel.
	 * 
	 * @author Pete Bankhead
	 */
	public static class ChannelThreshold {
		
		private final int channel;
		private final float minThreshold;
		private final float maxThreshold;
		
		private ChannelThreshold(int channel, float minThreshold, float maxThreshold) {
			this.channel = channel;
			this.minThreshold = minThreshold;
			this.maxThreshold = maxThreshold;
		}
		
		/**
		 * Create a simple channel threshold. This contains no intensity values (min/max thresholds are infinity) 
		 * but it is useful for thresholding classification images. In this case, the channel refers to the classification 
		 * label.
		 * @param channel
		 * @return
		 */
		public static ChannelThreshold create(int channel) {
			return new ChannelThreshold(channel, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
		}

		/**
		 * Create a threshold to select values between a minimum and maximum (inclusive).
		 * @param channel channel to threshold
		 * @param minThreshold minimum value (inclusive)
		 * @param maxThreshold maximum value (inclusive)
		 * @return
		 */
		public static ChannelThreshold create(int channel, float minThreshold, float maxThreshold) {
			return new ChannelThreshold(channel, minThreshold, maxThreshold);
		}

		/**
		 * Create a threshold to select values greater than or equal to a specified threshold.
		 * @param channel the channel to threshold
		 * @param minThreshold the minimum threshold to apply
		 * @return
		 */
		public static ChannelThreshold createAbove(int channel, float minThreshold) {
			return new ChannelThreshold(channel, minThreshold, Float.POSITIVE_INFINITY);
		}

		/**
		 * Create a threshold to select values less than or equal to a specified threshold.
		 * @param channel the channel to threshold
		 * @param maxThreshold the maximum threshold to apply
		 * @return
		 */
		public static ChannelThreshold createBelow(int channel, float maxThreshold) {
			return new ChannelThreshold(channel, Float.NEGATIVE_INFINITY, maxThreshold);
		}
		
		/**
		 * Create a threshold to select values that are exactly equal to a specified threshold.
		 * @param channel the channel to threshold
		 * @param threshold the threshold value
		 * @return
		 */
		public static ChannelThreshold createExactly(int channel, float threshold) {
			return new ChannelThreshold(channel, threshold, threshold);
		}

		/**
		 * Get the minimum threshold value. This may be {@link Float#NEGATIVE_INFINITY} if no minimum threshold is required.
		 * @return
		 */
		public float getMinThreshold() {
			return minThreshold;
		}
		
		/**
		 * Get the maximum threshold value. This may be {@link Float#POSITIVE_INFINITY} if no minimum threshold is required.
		 * @return
		 */
		public float getMaxThreshold() {
			return maxThreshold;
		}
		
		/**
		 * Get the channel to threshold.
		 * @return
		 */
		public int getChannel() {
			return channel;
		}
		
		@Override
		public String toString() {
			return String.format("Threshold %d (%s, %s)", channel, GeneralTools.formatNumber(minThreshold, 3), GeneralTools.formatNumber(maxThreshold, 3));
		}
		
	}
	
	
	// Beginnings of a builder class (incomplete and unused)
//	public static Tracer createTracer(ImageServer<BufferedImage> server) {
//		return new Tracer(server);
//	}
//	
//	public static class Tracer {
//		
//		private ImageServer<BufferedImage> server;
//		private List<ChannelThreshold> thresholds = new ArrayList<>();
//		private RegionRequest region;
//		
//		private Tracer(ImageServer<BufferedImage> server) {
//			this.server = server;
//		}
//		
//		public Tracer channel(int channel, float minThreshold, float maxThreshold) {
//			return channels(ChannelThreshold.create(channel, minThreshold, maxThreshold));
//		}
//		
//		public Tracer channels(ChannelThreshold... thresholds) {
//			for (var c : thresholds)
//				this.thresholds.add(c);
//			return this;
//		}
//		
//		public Tracer downsample(double downsample) {
//			if (region == null)
//				region = RegionRequest.createInstance(server, downsample);
//			else
//				region = region.updateDownsample(downsample);
//			return this;
//		}
//		
//		public Tracer region(RegionRequest region) {
//			this.region = region;
//			return this;
//		}
//		
//		public Geometry traceGeometry(Geometry clipArea) {
//			if (thresholds.isEmpty())
//				throw new IllegalArgumentException("No thresholds have been specified!");
//			var threshold = thresholds.get(0);
//			return ContourTracing.traceGeometry(server, region, clipArea, threshold.channel, threshold.minThreshold, threshold.maxThreshold);
//		}
//		
//		public Map<Integer, Geometry> traceAllGeometries(Geometry clipArea) {
//			if (thresholds.isEmpty())
//				throw new IllegalArgumentException("No thresholds have been specified!");
//			return ContourTracing.traceGeometries(server, region, clipArea, thresholds.toArray(ChannelThreshold[]::new));			
//		}
//		
//	}
	

	/**
	 * Trace a geometry from a single channel of a single image.
	 * @param server
	 * @param regionRequest
	 * @param clipArea
	 * @param channel
	 * @param minThreshold
	 * @param maxThreshold
	 * @return
	 */
	public static Geometry traceGeometry(ImageServer<BufferedImage> server, RegionRequest regionRequest, Geometry clipArea, int channel, float minThreshold, float maxThreshold) {
		var map = traceGeometries(server, regionRequest, clipArea, ChannelThreshold.create(channel, minThreshold, maxThreshold));
		if (map == null || map.isEmpty())
			return GeometryTools.getDefaultFactory().createPolygon();
		assert map.size() == 1;
		return map.values().iterator().next();
	}
	
	
	/**
	 * Trace one or more geometries in an image.
	 * @param server
	 * @param regionRequest optional region defining the area within which geometries should be traced
	 * @param clipArea optional clip region, intersected with the created geometries (may be null)
	 * @param thresholds
	 * @return
	 */
	public static Map<Integer, Geometry> traceGeometries(ImageServer<BufferedImage> server, RegionRequest regionRequest, Geometry clipArea, ChannelThreshold... thresholds) {
		
		RegionRequest region = regionRequest;
		if (region == null) {
			if (clipArea == null) {
				region = RegionRequest.createInstance(server, server.getDownsampleForResolution(0));
			} else {
				var env = clipArea.getEnvelopeInternal();
				region = RegionRequest.createInstance(server.getPath(), server.getDownsampleForResolution(0), GeometryTools.envelopToRegion(env, 0, 0));
			}
		} else if (clipArea != null) {
			// Ensure we don't compute more than we need to
			var env = clipArea.getEnvelopeInternal();
			region = region.intersect2D(GeometryTools.envelopToRegion(env, region.getZ(), region.getT()));
		}
		
		Collection<TileRequest> tiles = server.getTileRequestManager().getTileRequests(region);

		if (thresholds.length == 0 || tiles.isEmpty())
			return Collections.emptyMap();

		// If the region downsample doesn't match the tile requests, the scaling may be off
		// One way to resolve that (without requiring the region to be read in one go) is to generate new tile requests for a pyramidalized server at the correct resolution
		double downsample = region.getDownsample();
		if (Math.abs(tiles.iterator().next().getDownsample() - downsample) > 1e-3) {
			server = ImageServers.pyramidalize(server, downsample);
			tiles = server.getTileRequestManager().getTileRequests(region);
		}
		
		return traceGeometriesImpl(server, tiles, clipArea, thresholds);
		
		// TODO: Consider restricting parallelization
//		int nThreads = Math.min(Math.max(1, Math.max(thresholds.length, tiles.size())), Runtime.getRuntime().availableProcessors());
//		var pool = new ForkJoinPool(nThreads);
//		var task = pool.submit(() -> traceGeometriesImpl(server, tiles, clipArea, thresholds));
//		pool.shutdown();
//		try {
//			return task.get();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (ExecutionException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	
	@SuppressWarnings("unchecked")
	private static Map<Integer, Geometry> traceGeometriesImpl(ImageServer<BufferedImage> server, Collection<TileRequest> tiles, Geometry clipArea, ChannelThreshold... thresholds) {
		
		if (thresholds.length == 0)
			return Collections.emptyMap();
		
		Map<Integer, List<GeometryWrapper>> geometryMap = tiles.parallelStream()
				.map(t -> traceGeometries(server, t, clipArea, thresholds))
				.flatMap(p -> p.stream())
				.collect(Collectors.groupingBy(g -> g.label));
		
		Map<Integer, Geometry> output = new LinkedHashMap<>();
	
		// Determine 'inter-tile boundaries' - union operations can be very slow, so we want to restrict them 
		// only to geometries that really require them.
		var xBoundsSet = new TreeSet<Integer>();
		var yBoundsSet = new TreeSet<Integer>();
		for (var t : tiles) {
			xBoundsSet.add(t.getImageX());
			xBoundsSet.add(t.getImageX() + t.getImageWidth());
			yBoundsSet.add(t.getImageY());
			yBoundsSet.add(t.getImageY() + t.getImageHeight());
		}
		int[] xBounds = xBoundsSet.stream().mapToInt(x -> x).toArray(); 
		int[] yBounds = yBoundsSet.stream().mapToInt(y -> y).toArray(); 
		
		
		// Merge objects with the same classification
		for (var entry : geometryMap.entrySet()) {
			var list = entry.getValue();
			
			// If we just have one tile, that's what we need
			Geometry geometry = null;
			
			if (list.isEmpty())
				continue;
			if (list.size() == 1) {
				geometry = list.get(0).geometry;
			} else {
				logger.debug("Merging geometries from {} tiles for {}", list.size(), entry.getKey());
				
				var factory = list.get(0).geometry.getFactory();
				
				// Merge everything quickly into a single geometry
				var allPolygons = new ArrayList<Polygon>();
				for (var temp : list)
					PolygonExtracter.getPolygons(temp.geometry, allPolygons);
				
				// TODO: Explore where buffering is faster than union; if we can get rules for this it can be used instead
				boolean onlyBuffer = false;
				
				if (onlyBuffer) {
					var singleGeometry = factory.buildGeometry(allPolygons);
					geometry = singleGeometry.buffer(0);
				} else {
					
					// Unioning is expensive, so we just want to do it where really needed
					var tree = new Quadtree();
					for (var p : allPolygons) {
						tree.insert(p.getEnvelopeInternal(), p);
					}
					var env = new Envelope();
					
					var toMerge = new HashSet<Polygon>();
					for (int yi = 1; yi < yBounds.length-1; yi++) {
						env.init(xBounds[0]-1, xBounds[xBounds.length-1]+1, yBounds[yi]-1, yBounds[yi]+1);
						var items = tree.query(env);
						if (items.size() > 1)
							toMerge.addAll(items);
					}
					for (int xi = 1; xi < xBounds.length-1; xi++) {
						env.init(xBounds[xi]-1, xBounds[xi]+1, yBounds[0]-1, yBounds[yBounds.length-1]+1);
						var items = tree.query(env);
						if (items.size() > 1)
							toMerge.addAll(items);
					}
					if (!toMerge.isEmpty()) {
						logger.debug("Computing union for {}/{} polygons", toMerge.size(), allPolygons.size());
						var mergedGeometry = GeometryTools.union(toMerge);
//						System.err.println("To merge: " + toMerge.size());
//						var mergedGeometry = factory.buildGeometry(toMerge).buffer(0);
						var iter = allPolygons.iterator();
						while (iter.hasNext()) {
							if (toMerge.contains(iter.next()))
								iter.remove();
						}
						allPolygons.removeAll(toMerge);
						var newPolygons = new ArrayList<Polygon>();
						PolygonExtracter.getPolygons(mergedGeometry, newPolygons);
						allPolygons.addAll(newPolygons);
					}
					geometry = factory.buildGeometry(allPolygons);				
					geometry.normalize();
				}
				
			}
			
			output.put(entry.getKey(), geometry);
		}
		return output;
	}
	

	private static List<GeometryWrapper> traceGeometries(ImageServer<BufferedImage> server, TileRequest tile, Geometry clipArea, ChannelThreshold... thresholds) {
		try {
			return traceGeometriesImpl(server, tile, clipArea, thresholds);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	
	private static List<GeometryWrapper> traceGeometriesImpl(ImageServer<BufferedImage> server, TileRequest tile, Geometry clipArea, ChannelThreshold... thresholds) throws IOException {
		if (thresholds.length == 0)
			return Collections.emptyList();
		
		var request = tile.getRegionRequest();
		var list = new ArrayList<GeometryWrapper>();

		var img = server.readBufferedImage(request);
		// Get an image to threshold
		var channelType = server.getMetadata().getChannelType();
		int h = img.getHeight();
		int w = img.getWidth();
		
		// If we have probabilities, then the 'true' classification is the one with the highest values.
		// If we have classifications, then the 'true' classification is the value of the pixel (which is expected to have a single band).
		boolean doClassification = channelType == ImageServerMetadata.ChannelType.PROBABILITY || channelType == ImageServerMetadata.ChannelType.CLASSIFICATION;
		if (doClassification) {
			SimpleImage image;
			if (channelType == ImageServerMetadata.ChannelType.PROBABILITY) {
				// Convert probabilities to classifications
				var raster = img.getRaster();
				var nChannels = server.nChannels();
				float[] output = new float[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int maxInd = 0;
						float maxVal = raster.getSampleFloat(x, y, 0);
						for (int c = 1; c < nChannels; c++) {
							float val = raster.getSampleFloat(x, y, c);						
							if (val > maxVal) {
								maxInd = c;
								maxVal = val;
							}
							output[y*w+x] = (float)maxInd;
						}
					}
				}
				image = SimpleImages.createFloatImage(output, w, h);
			} else {
				// Handle classifications
				var raster = img.getRaster();
				var pixels = raster.getSamples(0, 0, w, h, 0, (float[])null);
				image = SimpleImages.createFloatImage(pixels, w, h);
			}
			for (var threshold : thresholds) {
				int c = threshold.getChannel();
				Geometry geometry = ContourTracing.createTracedGeometry(image, c, c, request);
				if (geometry != null && !geometry.isEmpty()) {
					if (clipArea != null) {
						geometry = GeometryTools.attemptOperation(geometry, g -> g.intersection(clipArea));
						geometry = GeometryTools.homogenizeGeometryCollection(geometry);
					}
					if (!geometry.isEmpty() && geometry.getArea() > 0) {
						// Exclude lines/points that can sometimes arise
						list.add(new GeometryWrapper(geometry, c));
					}
				}
			}
		} else {
			// Apply the provided threshold to all channels
			var raster = img.getRaster();
			for (var threshold : thresholds) {
				Geometry geometry = ContourTracing.createTracedGeometry(
						raster, threshold.getMinThreshold(), threshold.getMaxThreshold(), threshold.getChannel(), request);
				if (geometry != null) {
					if (clipArea != null) {
						geometry = GeometryTools.attemptOperation(geometry, g -> g.intersection(clipArea));
						geometry = GeometryTools.homogenizeGeometryCollection(geometry);
					}
					if (!geometry.isEmpty() && geometry.getArea() > 0) {
						// Exclude lines/points that can sometimes arise
						list.add(new GeometryWrapper(geometry, threshold.getChannel()));
					}
				}
			}
			
		}
		return list;
	}
	

	/**
	 * Simple wrapper for a geometry and an label (usually a channel number of classification).
	 */
	private static class GeometryWrapper {
		
		final Geometry geometry;
		final int label;
		
		private GeometryWrapper(Geometry geometry, int label) {
			this.geometry = geometry;
			this.label = label;
		}
		
	}
	
	
	
	
	private static boolean selected(float v, float min, float max) {
		return v >= min && v <= max;
	}
	
	
	
	/**
	 * This is adapted from ImageJ's ThresholdToSelection.java (public domain) written by Johannes E. Schindelin 
	 * based on a proposal by Tom Larkworthy.
	 * <p>
	 * See https://github.com/imagej/imagej1/blob/573ab799ae8deb0f4feb79724a5a6f82f60cd2d6/ij/plugin/filter/ThresholdToSelection.java
	 * <p>
	 * The code has been substantially rewritten to enable more efficient use within QuPath and to use Java Topology Suite.
	 * 
	 * @param image
	 * @param min
	 * @param max
	 * @param xOffset
	 * @param yOffset
	 * @return
	 */
	private static Geometry traceGeometry(SimpleImage image, float min, float max, double xOffset, double yOffset) {
		
		int w = image.getWidth();
		int h = image.getHeight();
		
		boolean[] prevRow, thisRow;
		var manager = new GeometryManager(GeometryTools.getDefaultFactory());

		// Cache for the current and previous thresholded rows
		prevRow = new boolean[w + 2];
		thisRow = new boolean[w + 2];
		
		// Current outlines
		Outline[] movingDown = new Outline[w + 1];
		Outline movingRight = null;
		
		int pixelCount = 0;
		
		for (int y = 0; y <= h; y++) {
			
			// Swap this and previous rows (this row data will be overwritten as we go)
			boolean[] tempSwap = prevRow;
			prevRow = thisRow;
			thisRow = tempSwap;
			
//			thisRow[1] = y < h ? selected(raster, 0, y, min, max) : false;
			thisRow[1] = y < h ? selected(image.getValue(0, y), min, max) : false;
			
			for (int x = 0; x <= w; x++) {
				
				int left = x;
				int center = x + 1;
				int right = x + 2;
				
				if (y < h && x < w - 1)
					thisRow[right] = selected(image.getValue(x+1, y), min, max);  //we need to read one pixel ahead
//					thisRow[right] = selected(raster, center, y, min, max);  //we need to read one pixel ahead
				else if (x < w - 1)
					thisRow[right] = false;
				
				if (thisRow[center])
					pixelCount++;
									
				/*
				 * Pixels are considered in terms of a 2x2 square.
				 * ----0----
				 * | A | B |
				 * 0---X====
				 * | C | D |
				 * ----=====
				 * 
				 * The current focus is on D, which is considered the 'center' (since subsequent 
				 * pixels matter too for the pattern, but we don't need them during this iteration).
				 * 
				 * In each case, the question is whether or not an outline will be created,
				 * or moved for a location 0 to location X - possibly involving merges or completion of 
				 * an outline.
				 * 
				 * Note that outlines are always drawn so that the 'on' pixels are on the left, 
				 * from the point of view of the directed line.
				 * Therefore shells are anticlockwise whereas holes are clockwise.
				 */
				
				// Extract the local 2x2 binary pattern
				// This represented by a value between 0 and 15, where bits indicate if a pixel is selected or not
				int pattern = (prevRow[left] ? 8 : 0) 
								+ (prevRow[center] ? 4 : 0) 
								+ (thisRow[left] ? 2 : 0)
								+ (thisRow[center] ? 1 : 0);

				
				switch (pattern) {
				case 0: 
					// Nothing selected
//					assert movingDown[x] == null;
//					assert movingRight == null;
					break;
				case 1: 
					// Selected D
//					assert movingDown[x] == null;
//					assert movingRight == null;
					// Create new shell
					movingRight = new Outline(xOffset, yOffset);
					movingRight.append(x, y);
					movingDown[x] = movingRight;
					break;
				case 2: 
					// Selected C
//					assert movingDown[x] == null;
					movingRight.prepend(x, y);
					movingDown[x] = movingRight;
					movingRight = null;
					break;
				case 3: 
					// Selected C, D
//					assert movingDown[x] == null;
//					assert movingRight != null;
					break;
				case 4: 
					// Selected B
//					assert movingRight == null;
					movingDown[x].append(x, y);
					movingRight = movingDown[x];
					movingDown[x] = null;
					break;
				case 5: 
					// Selected B, D
//					assert movingRight == null;
//					assert movingDown[x] != null;
					break;
				case 6: 
					// Selected B, C
//					assert movingDown[x] != null;
//					assert movingRight != null;
					movingRight.prepend(x, y);
					if (Objects.equals(movingRight, movingDown[x])) {
						// Hole completed!
						manager.addHole(movingRight);
						movingRight = new Outline(xOffset, yOffset);
						movingRight.append(x, y);
						movingDown[x] = movingRight;
					} else {
						movingDown[x].append(x, y);
						var temp = movingRight;
						movingRight = movingDown[x];
						movingDown[x] = temp;
					}
					break;
				case 7: 
					// Selected B, C, D
//					assert movingDown[x] != null;
//					assert movingRight != null;
					movingDown[x].append(x, y);
					if (Objects.equals(movingRight, movingDown[x])) {
						// Hole completed!
						manager.addHole(movingRight);
					} else {
						movingRight.prepend(movingDown[x]);
						replace(movingDown, movingDown[x], movingRight);
					}
					movingRight = null;
					movingDown[x] = null;
					break;
				case 8: 
					// Selected A
//					assert movingDown[x] != null;
//					assert movingRight != null;
					movingRight.append(x, y);
					if (Objects.equals(movingRight, movingDown[x])) {
						// Shell completed!
						manager.addShell(movingRight);
					} else {
						movingDown[x].prepend(movingRight);
						replace(movingDown, movingRight, movingDown[x]);
					}
					movingRight = null;
					movingDown[x] = null;
					break;
				case 9: 
					// Selected A, D
//					assert movingDown[x] != null;
//					assert movingRight != null;
					movingRight.append(x, y);
					if (Objects.equals(movingRight, movingDown[x])) {
						// Shell completed!
						manager.addShell(movingRight);
						movingRight = new Outline(xOffset, yOffset);
						movingRight.append(x, y);
						movingDown[x] = movingRight;
					} else {
						movingDown[x].prepend(x, y);
						var temp = movingRight;
						movingRight = movingDown[x];
						movingDown[x] = temp;
					}
					break;
				case 10: 
					// Selected A, C
//					assert movingRight == null;
//					assert movingDown[x] != null;
					break;
				case 11: 
					// Selected A, C, D
//					assert movingRight == null;
//					assert movingDown[x] != null;
					movingDown[x].prepend(x, y);
					movingRight = movingDown[x];
					movingDown[x] = null;
					break;
				case 12: 
					// Selected A, B
//					assert movingDown[x] == null;
//					assert movingRight != null;
					break;
				case 13: 
					// Selected A, B, D
//					assert movingDown[x] == null;
//					assert movingRight != null;
					movingRight.append(x, y);
					movingDown[x] = movingRight;
					movingRight = null;
					break;
				case 14: 
					// Selected A, B, C
//					assert movingRight == null;
//					assert movingDown[x] == null;
					// Create new hole
					movingRight = new Outline(xOffset, yOffset);
					movingRight.append(x, y);
					movingDown[x] = movingRight;
					break;
				case 15: 
					// Selected A, B, C, D
//					assert movingDown[x] == null;
//					assert movingRight == null;
					break;
				}
			}
		}
		
		var geom = manager.getFinalGeometry();
		
		var area = geom.getArea();
		if (pixelCount != area) {
			logger.warn("Pixel count {} is not equal to geometry area {}", pixelCount, area);
		}
		
		return geom;

	}
	
	
	private static void replace(Outline[] outlines, Outline original, Outline replacement) {
		for (int i = 0; i < outlines.length; i++) {
			if (outlines[i] == original)
				outlines[i] = replacement;
		}
	}
	
	
	private static class GeometryManager {

		private Polygonizer polygonizer = new Polygonizer(true);
		private GeometryFactory factory;
		
		private List<LineString> lines = new ArrayList<>();

		GeometryManager(GeometryFactory factory) {
			this.factory = factory;
		}

		public void addHole(Outline outline) {
			addOutline(outline, true);
		}

		public void addShell(Outline outline) {
			addOutline(outline, false);
		}

		private void addOutline(Outline outline, boolean isHole) {
			lines.add(factory.createLineString(outline.getRing()));
		}

		public Geometry getFinalGeometry() {
			var geomTemp = factory.buildGeometry(lines).union();
			polygonizer.add(geomTemp);
			return polygonizer.getGeometry();
		}

	}

	
	

	private static class Outline {

		private Deque<Coordinate> coords = new ArrayDeque<>();

		private double xOffset, yOffset;

		/**
		 * Initialize an output. Optional x and y offsets may be provided, in which case
		 * these will be added to coordinates. The reason for this is to help support 
		 * working with tiled images, where the tile origin is not 0,0 but we don't want to 
		 * have to handle this elsewhere.
		 * 
		 * @param xOffset
		 * @param yOffset
		 */
		public Outline(double xOffset, double yOffset) {
			this.xOffset = xOffset;
			this.yOffset = yOffset;
		}

		public void append(int x, int y) {
			append(new Coordinate(xOffset + x, yOffset + y));
		}

		public void append(Coordinate c) {
			// Don't add repeating coordinate
			if (!coords.isEmpty() && coords.getLast().equals(c))
				return;
			coords.addLast(c);
		}


		public void prepend(int x, int y) {
			prepend(new Coordinate(xOffset + x, yOffset + y));
		}

		public void prepend(Coordinate c) {
			// Don't add repeating coordinate
			if (!coords.isEmpty() && coords.getFirst().equals(c))
				return;
			coords.addFirst(c);
		}

		public void prepend(Outline outline) {
			outline.coords.descendingIterator().forEachRemaining(c -> prepend(c));
			// Update the coordinate array for the other - since they are now part of the same outline
			outline.coords = coords;
		}

		public Coordinate[] getRing() {
			if (!coords.getFirst().equals(coords.getLast()))
				coords.add(coords.getFirst());
			return coords.toArray(Coordinate[]::new);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((coords == null) ? 0 : coords.hashCode());
			long temp;
			temp = Double.doubleToLongBits(xOffset);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			temp = Double.doubleToLongBits(yOffset);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Outline other = (Outline) obj;
			if (coords == null) {
				if (other.coords != null)
					return false;
			} else if (!coords.equals(other.coords))
				return false;
			if (Double.doubleToLongBits(xOffset) != Double.doubleToLongBits(other.xOffset))
				return false;
			if (Double.doubleToLongBits(yOffset) != Double.doubleToLongBits(other.yOffset))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "[" + coords.stream()
			.map(c -> "(" + GeneralTools.formatNumber(c.x, 2) + ", " + GeneralTools.formatNumber(c.y, 2) + ")")
			.collect(Collectors.joining(", ")) + "]";
		}


	}

}
