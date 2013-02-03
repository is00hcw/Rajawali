package rajawali.parser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import rajawali.materials.TextureManager;
import rajawali.renderer.RajawaliRenderer;
import rajawali.util.LittleEndianDataInputStream;
import rajawali.util.RajLog;
import android.content.res.Resources;

/**
 * STL Parser written using the ASCII format as describe on Wikipedia.
 * 
 * http://en.wikipedia.org/wiki/STL_(file_format)
 * 
 * @author Ian Thomas - toxicbakery@gmail.com
 */
public class StlParser extends AMeshParser {

	private final List<Float> vertices = new ArrayList<Float>();
	private final List<Float> normals = new ArrayList<Float>();

	public StlParser(RajawaliRenderer renderer, String fileOnSDCard) {
		super(renderer, fileOnSDCard);
	}

	public StlParser(Resources resources, TextureManager textureManager, int resourceId) {
		super(resources, textureManager, resourceId);
	}

	@Override
	public AMeshParser parse() {
		super.parse();

		try {
			// Open the file
			BufferedReader buffer = null;
			InputStream fileIn = null;
			if (mFile == null) {
				fileIn = mResources.openRawResource(mResourceId);
				buffer = new BufferedReader(new InputStreamReader(fileIn));
			} else {
				buffer = new BufferedReader(new FileReader(mFile));
			}

			String line = buffer.readLine();

			// Determine if ASCII or Binary
			boolean isASCII = false;
			char[] readAhead = new char[300];
			buffer.mark(readAhead.length);
			buffer.read(readAhead, 0, readAhead.length);
			String readAheadString = new String(readAhead);
			if (readAheadString.contains("facet normal") && readAheadString.contains("outer loop"))
				isASCII = true;
			buffer.reset();

			// Determine ASCII or Binary format
			if (isASCII) {
				/**
				 * ASCII
				 */

				RajLog.i("StlPaser: Reading ASCII");

				int nextOffset, prevOffset, i, insert;
				float[] tempNorms = new float[3];

				// Read the facet
				while ((line = buffer.readLine()) != null) {

					if (line.contains("facet normal")) {

						nextOffset = line.lastIndexOf(" ");
						tempNorms[2] = Float.parseFloat(line.substring(nextOffset + 1));

						prevOffset = nextOffset;
						nextOffset = line.lastIndexOf(" ", prevOffset - 1);
						tempNorms[1] = Float.parseFloat(line.substring(nextOffset + 1, prevOffset));

						prevOffset = nextOffset;
						nextOffset = line.lastIndexOf(" ", prevOffset - 1);
						tempNorms[0] = Float.parseFloat(line.substring(nextOffset + 1, prevOffset));

						// Need to duplicate the normal for each vertex of the triangle
						for (i = 0; i < 3; i++) {
							normals.add(tempNorms[0]);
							normals.add(tempNorms[1]);
							normals.add(tempNorms[2]);
						}

					} else if (line.contains("vertex")) {

						insert = vertices.size();

						nextOffset = line.lastIndexOf(" ");
						vertices.add(Float.parseFloat(line.substring(nextOffset + 1)));

						prevOffset = nextOffset;
						nextOffset = line.lastIndexOf(" ", prevOffset - 1);
						vertices.add(insert, Float.parseFloat(line.substring(nextOffset + 1, prevOffset)));

						prevOffset = nextOffset;
						nextOffset = line.lastIndexOf(" ", prevOffset - 1);
						vertices.add(insert, Float.parseFloat(line.substring(nextOffset + 1, prevOffset)));
					}
				}

				float[] verticesArr = new float[vertices.size()];
				float[] normalsArr = new float[normals.size()];
				int[] indicesArr = new int[verticesArr.length / 3];

				for (i = 0; i < verticesArr.length; i++) {
					verticesArr[i] = vertices.get(i);
					normalsArr[i] = normals.get(i);
				}

				for (i = 0; i < indicesArr.length; i++)
					indicesArr[i] = i;

				mRootObject.setData(verticesArr, normalsArr, null, null, indicesArr);

				// Cleanup
				buffer.close();
				fileIn.close();

			} else {
				/**
				 * BINARY
				 */
				RajLog.i("StlPaser: Reading Binary");
				// Switch to a DataInputStream
				buffer.close();
				fileIn.close();

				LittleEndianDataInputStream dis = null;
				if (mFile == null) {
					fileIn = mResources.openRawResource(mResourceId);
					dis = new LittleEndianDataInputStream(fileIn);
				} else {
					dis = new LittleEndianDataInputStream(new FileInputStream(mFile));
				}

				// Skip the header
				dis.skip(80);

				// Read the number of facets (have to convert the uint to a long
				int facetCount = dis.readInt();

				float[] verticesArr = new float[facetCount * 9];
				float[] normalsArr = new float[facetCount * 9];
				int[] indicesArr = new int[facetCount * 3];
				float[] tempNorms = new float[3];
				int vertPos = 0, normPos = 0;

				for (int i = 0; i < indicesArr.length; i++)
					indicesArr[i] = i;

				// Read all the facets
				while(dis.available() > 0) {
					
					// Read normals
					for (int j = 0; j < 3; j++) {
						tempNorms[j] = dis.readFloat();
						if (Float.isNaN(tempNorms[j]) || Float.isInfinite(tempNorms[j])) {
							RajLog.w("STL contains bad normals of NaN or Infinite!");
							tempNorms[0] = 0;
							tempNorms[1] = 0;
							tempNorms[2] = 0;
							break;
						}
					}
					
					for (int j = 0; j < 3; j++) {
						normalsArr[normPos++] = tempNorms[0];
						normalsArr[normPos++] = tempNorms[1];
						normalsArr[normPos++] = tempNorms[2];
					}
					
					// Read vertices
					for (int j = 0; j<9;j++)
						verticesArr[vertPos++] = dis.readFloat();
					
					dis.skip(2);
				}
				
				mRootObject.setData(verticesArr, normalsArr, null, null, indicesArr);

				// Cleanup
				dis.close();
				fileIn.close();
			}

		} catch (FileNotFoundException e) {
			RajLog.e("[" + getClass().getCanonicalName() + "] Could not find file.");
			e.printStackTrace();
		} catch (NumberFormatException e) {
			// Failed to parse a number
			e.printStackTrace();
		} catch (IOException e) {
			// File Errors
			e.printStackTrace();
		} catch (Exception e) {
			// o.O
			e.printStackTrace();
		}

		return this;
	}

	public static final class StlParseException extends Exception {

		private static final long serialVersionUID = -5098120548044169618L;

		public StlParseException(String msg) {
			super(msg);
		}
	}

}
