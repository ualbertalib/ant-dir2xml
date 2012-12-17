package ca.ualberta.library.digital.dir2xml;

import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.BuildException;

import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
//SAX classes.
import org.xml.sax.*;
import org.xml.sax.helpers.*;
//JAXP 1.1
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.sax.*; 

public class Dir2xml extends MatchingTask {
	private String dirpath;

	private String file;

	private SimpleDateFormat format;

	private Date date;

	private BufferedWriter out;

	private int depth = 1;

	private boolean md5 = false;
	private boolean dimensions = false;
	
	private TransformerHandler hd = null;

	// The method executing the task
	public void execute() throws BuildException {

		format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		format.setTimeZone(TimeZone.getTimeZone("GMT"));
		date = new Date();
		try {
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(file), "UTF-8"));
			
			 StreamResult streamResult = new StreamResult(out);
			 SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
//			  SAX2.0 ContentHandler.
			 hd = tf.newTransformerHandler();
			 Transformer serializer = hd.getTransformer();
			 serializer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
			 serializer.setOutputProperty(OutputKeys.INDENT,"yes");
			 hd.setResult(streamResult);
			 hd.startDocument();
			 
			// out.write("<?xml version='1.0' encoding='UTF-8'?>\n");
			File dir = new File(dirpath);

			handleDir(dir, 1);

			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void handleDir(File dir, int curdepth) {
		int id = 1;
		try {
			date.setTime(dir.lastModified());

			//out.write("<dir path='" + dir.getCanonicalPath()
			//		+ "' lastModified='" + format.format(date).toString()
			//		+ "'>\n");
			AttributesImpl dirAtts = new AttributesImpl();
			dirAtts.addAttribute("", "", "path", "CDATA", dir.getCanonicalPath());
			dirAtts.addAttribute("", "", "lastModified", "CDATA", format.format(date).toString());
			try {
				hd.startElement("", "", "dir", dirAtts);
			}
			catch (Exception e) { }
			if (curdepth <= depth) {
				File[] children = dir.listFiles();

				if (children == null) {
					// Either dir does not exist or is not a directory
				} else {
					for (int i = 0; i < children.length; i++) {
						// Get filename of file or directory
						// System.out.println("child " + i + ": " +
						// children[i].getName());
						try {
							if (children[i].isFile()) {
								// if hidden, ignore
								if (!children[i].isHidden()) {
									InputStream is = new FileInputStream(
											children[i]);

									date.setTime(children[i].lastModified());

									AttributesImpl fileAtts = new AttributesImpl();
									fileAtts.addAttribute("", "", "id", "CDATA", Integer.toString(id));
									fileAtts.addAttribute("", "", "name", "CDATA", children[i].getName());
									fileAtts.addAttribute("", "", "lastModified", "CDATA", format.format(date).toString());
									fileAtts.addAttribute("", "", "length", "CDATA", Long.toString(children[i].length()));
									//out.write("<file id='" + id + "' name='");
									//out.write(children[i].getName() + "' ");
									//out.write("lastModified='");
									//out.write(format.format(date).toString()
									//		+ "' ");
									//out.write("length='" + children[i].length()
									//		+ "'");
									if (md5) {
										// calculate md5 digest for this file
										//out.write(" md5='");
										try {
											fileAtts.addAttribute("", "", "md5", "CDATA", getMD5(is));
											//out.write(getMD5(is));
										} catch (Exception e) {
											// read errors etc.
											out.write("md5 error");
										}
										//out.write("'");
									}
									// if it's an image, get width and height
									if (dimensions) {
										String fname = children[i].getName();
										int finalPeriod = fname
												.lastIndexOf('.');
										if (finalPeriod >= 0) {
											// fname has an extension
											String fextension = fname
													.substring(finalPeriod + 1)
													.toLowerCase();
											// System.out.println(children[i] +
											// ":
											// extension " + fextension);
											try {
												Iterator readers = ImageIO
														.getImageReadersByFormatName(fextension);
												ImageReader reader = (ImageReader) readers
														.next();
												ImageInputStream iis = ImageIO
														.createImageInputStream(children[i]);
												reader.setInput(iis, true);
												int w = reader.getWidth(0);
												int h = reader.getHeight(0);
												//out.write(" width='" + w + "' height='" + h + "'");
												fileAtts.addAttribute("", "", "width", "CDATA", Integer.toString(w));
												fileAtts.addAttribute("", "", "height", "CDATA", Integer.toString(h));
											} catch (Exception e) {
												// ok, maybe it wasn't an image,
												// so
												// forget about it
												// System.out.println("not an
												// image");
												// System.out.println(e);
											}
										}
									}

									//out.write("/>\n");
									hd.startElement("", "", "file", fileAtts);
									hd.endElement("", "", "file");
									id++;
								}

							} else if (children[i].isDirectory()) {
								// if hidden, ignore
								if (!children[i].isHidden())
									handleDir(children[i], curdepth + 1);
							}
						} catch (Exception e) {
							e.printStackTrace();

						}
					}
				}
			}
			//out.write("</dir>");
			try {
				hd.endElement("", "", "dir");
			}
			catch (Exception e) { }
		} catch (IOException e) {
		}

	}

	// adapted from http://www.javalobby.org/java/forums/t84420.html
	public String getMD5(InputStream is) throws NoSuchAlgorithmException,
			FileNotFoundException {
		MessageDigest digest = MessageDigest.getInstance("MD5");
		byte[] buffer = new byte[8192];
		int read = 0;
		try {
			while ((read = is.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
			byte[] md5sum = digest.digest();
			BigInteger bigInt = new BigInteger(1, md5sum);
			String output = bigInt.toString(16);
			// pad with zeroes to 32 chars, cuz that's how md5sum does it
			while (output.length() < 32)
				output = "0" + output;
			return output;
		} catch (IOException e) {
			throw new RuntimeException("Unable to process file for MD5", e);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				throw new RuntimeException(
						"Unable to close input stream for MD5 calculation", e);
			}
		}
	}

	// The setters for the target attributes
	public void setDirpath(String dirpath) {
		this.dirpath = dirpath;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public void setDepth(String depth) {
		try {
			this.depth = Integer.parseInt(depth);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setMD5(Boolean md5) {
		this.md5 = md5;
	}
	
	public void setDimensions(Boolean dimensions) {
		this.dimensions = dimensions;
	}
	
}
