package br.ufla.tmsmap;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.DirectLayer;
import org.geotools.map.MapContent;
import org.geotools.map.MapViewport;
import org.opengis.geometry.DirectPosition;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

/**
 * Created by rthoth on 21/10/15.
 */
public class TMSLayer implements Layer {

	private static final int TILE_WIDTH = 256;
	private static final int TILE_HEIGHT = 256;

	private final String baseUrl;
	private final int tileWidth;
	private final int tileHeight;
	private boolean debug = true;

	public TMSLayer(String url, int tileWidth, int tileHeight) throws MalformedURLException {
		assert url != null : "URL is null";
		assert tileHeight > 0 : "TileHeight must be greater than zero";
		assert tileWidth > 0 : "TileWidth must be greater than zero";

		this.baseUrl = url;
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
	}

	public TMSLayer(String url) throws MalformedURLException {
		this(url, TILE_WIDTH, TILE_HEIGHT);
	}

	public static TMSLayer from(String url) throws MalformedURLException {
		return new TMSLayer(url);
	}

	public static TMSLayer from(File file) throws MalformedURLException, UnsupportedEncodingException {
		return new TMSLayer(URLDecoder.decode(file.toURI().toURL().toString(), "UTF-8"));
	}

	@Override
	public DirectLayer createMapLayer(MapViewport viewport, int zoom) {
		return new TMSDirectLayer(viewport, zoom);
	}

	private URL urlOf(Tile tile) throws MalformedURLException {
		return new URL(baseUrl
				  .replace("{x}", String.valueOf(tile.x))
				  .replace("{y}", String.valueOf(tile.y))
				  .replace("{z}", String.valueOf(tile.z))
		);
	}

	public class TMSDirectLayer extends DirectLayer {
		private final int zoom;
		private final MapViewport viewport;

		public TMSDirectLayer(MapViewport viewport, int zoom) {
			this.viewport = viewport;
			this.zoom = zoom;
		}

		@Override
		public void draw(Graphics2D graphics, MapContent map, MapViewport viewport) {

			int x1, y1, x2, y2;

			DirectPosition lowerCorner = viewport.getBounds().getLowerCorner(),
					  upperCorner = viewport.getBounds().getUpperCorner();

			x1 = (int) (tileWidth * (SlippyUtil.lngToTileDouble(lowerCorner.getOrdinate(0), zoom)));
			x2 = (int) (tileWidth * (SlippyUtil.lngToTileDouble(upperCorner.getOrdinate(0), zoom)));

			y1 = (int) (tileHeight * (SlippyUtil.latToTileDouble(upperCorner.getOrdinate(1), zoom)));
			y2 = (int) (tileHeight * (SlippyUtil.latToTileDouble(lowerCorner.getOrdinate(1), zoom)));

			int width = x2 - x1, height = y2 - y1;

			TileRange tileRange = new TileRange(viewport.getBounds(), zoom);

			AffineTransform scaleTransform = new AffineTransform(),
					  transform = new AffineTransform();

			double xscale = viewport.getScreenArea().getWidth() / width,
					  yscale = viewport.getScreenArea().getHeight() / height;

			scaleTransform.scale(xscale, yscale);

			x1 = x1 % tileWidth;
			y1 = y1 % tileHeight;

			URL url;
			BufferedImage image;

			loop: for (Tile tile : tileRange) {

				transform.setToIdentity();

				x2 = tileWidth * (tile.x - tileRange.minX);
				y2 = tileHeight * (tile.y - tileRange.minY);

				transform.concatenate(scaleTransform);
				transform.translate(x2 - x1, y2 - y1);

				try {
					url = urlOf(tile);
				} catch (MalformedURLException e) {
					throw new TMSLayerException(this, tile, e);
				}

				try {
					image = ImageIO.read(url);
				} catch (IOException e) {
					Throwable cause = e;
					while (cause != null) {
						if (cause instanceof FileNotFoundException) {
							continue loop;
						}
						else {
							cause = cause.getCause();
						}
					}

					throw new TMSLayerException(this, tile, e);
				}

				try {
					graphics.drawImage(image, transform, null);
				} catch (Throwable throwable) {
					throw new TMSLayerException(this, tile, throwable);
				}
			}
		}

		@Override
		public ReferencedEnvelope getBounds() {
			return viewport.getBounds();
		}

		@Override
		public String toString() {
			return "TMS Layer(" + baseUrl + ")";
		}
	}
}
