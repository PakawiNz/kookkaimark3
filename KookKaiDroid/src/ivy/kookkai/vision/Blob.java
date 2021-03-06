package ivy.kookkai.vision;

import java.util.ArrayList;

import ivy.kookkai.data.GlobalVar;
import ivy.kookkai.debugview.DebugImgView;
import android.graphics.Rect;
import android.util.Log;

public class Blob {
	
	private static final int jumpAllowance = 60;
	private static final int minimumGreen = 20;
	
	private static final int cutTop = 75;
	
	private static final int cameraCentroidY = 10;
	private static final int cameraRad = 140;
	
	private int width, height;
	private final int qx[] = new int[86400];
	private byte[] colorData;
	private byte[] circleMask;
	private byte[] yImg;
	private byte[] img;
	private DebugImgView debugImg;
	private boolean drawcolor;
	
	private int[] convexL;
	private int[] convexR;

	public int dataWidth;
	public int dataHeight;
	String out = "";
	
	public Blob(){
		
	}
	
	public Blob(int w, int h,DebugImgView dView){
		colorData = new byte[w * h];
		convexL = new int[w];
		convexR = new int[w];
		debugImg = dView;
		width = w;
		height = h;
		dataHeight = w;
		dataWidth = h;
	}
	
	public byte[] getColorData() {
		return colorData;
	}

	public String execute(byte[] brightness, byte[] cbcrImg,  boolean drawColor) {
		out = "";

		yImg = brightness;
		img = cbcrImg;
		drawcolor = drawColor;
		
		threshold();
		drawColor();
		findBlob();
		ArrayList<BlobObject> finalresult = connectBlob();
		//filterNoise(finalresult);
		GlobalVar.mergeResult = finalresult;

		return out;
	}
	
	private void threshold() {

		int outIndex = 0;
		int y, cr, cb;
		int i, j, k;
		
		int firstGreen;
		int lastGreen;
		int greenCount;
		int nonGreenCount;

		int w2 = width * 2;
		int w4 = width * 4;
		for (i = 0; i < width; i++) {
			
			firstGreen = 0;
			lastGreen = 0;
			greenCount = 0;
			nonGreenCount = 0;
			
			convexL[i] = 0;
			convexR[i] = 0;
			
			for (j = i * 2 + w2 * (height - 1), k = i * 2 + w4 * (height - 1); j >= 0; j -= w2, k -= w4, outIndex++) {
				cr = (int) img[j] & 0xff;
				cb = (int) img[j + 1] & 0xff;
				y = (int) yImg[k] & 0xff;

				if (y > ColorManager.WHITE_THRESHOLD)
					colorData[outIndex] = ColorManager.WHITE;
				else if (y < ColorManager.BLACK_THRESHOLD)
					colorData[outIndex] = ColorManager.BLACK;
				else 
					colorData[outIndex] = ColorManager.crcbHashMap[cr][cb];
				
				/** CONVEX HULL MAKING [SPEED OPTIMIZE]*/
				
				if(colorData[outIndex] == ColorManager.GREEN){
					if(firstGreen == 0 || nonGreenCount > jumpAllowance)
						firstGreen = outIndex;
					lastGreen = outIndex;
					greenCount++;
					nonGreenCount = 0;
				}else if (colorData[outIndex] == ColorManager.UNDEFINE || 
						  colorData[outIndex] == ColorManager.WHITE ){
					nonGreenCount++;
				}
			}
			
			if(greenCount > minimumGreen){
				convexL[i] = firstGreen;
				convexR[i] = lastGreen;
			}
		}
	}

	private void drawColor(){
		// can't move to debugImgView coz vision blob will execute painting
		int outIndex = 0;
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++, outIndex++) {
				
//				if(!(convexL[i] < outIndex && outIndex < convexR[i]))							// delete NonConvex
//					colorData[outIndex] = ColorManager.UNDEFINE;
				
				if(i < cutTop)
					colorData[outIndex] = ColorManager.BLACK;
					
				if(Math.pow((i-width/2 - cameraCentroidY),2) + Math.pow((j-height/2),2) > Math.pow(cameraRad,2))	// draw Circle
					colorData[outIndex] = ColorManager.BLACK;
				
				if (drawcolor) 																	// draw all Circle
					debugImg.drawPixel(j, i, ColorManager.rColor[colorData[outIndex]]);
			}
		}
		
	}
	
	private void findBlob() {
		BlobObject b;
		GlobalVar.blobResult.clear();
		int outIndex = 0;
		
		for (int i = 0; i < width; i++) {
			for(int j = 0; j < height ; j++){
				// GREEN should be a defined color, but should not count as a blob
				if (convexL[i] < outIndex && outIndex < convexR[i]) {
					if(	colorData[outIndex] != ColorManager.UNDEFINE && (
						//colorData[outIndex] == ColorManager.MAGENTA || 
						//colorData[outIndex] == ColorManager.CYAN || 
						colorData[outIndex] == ColorManager.YELLOW || 
						colorData[outIndex] == ColorManager.ORANGE )) {			
					
						b = fillBlob(outIndex);
						if (b != null) {
							GlobalVar.blobResult.add(b);
							// debugImg.drawRect(b.posRect,Color.WHITE);
						}
					}
				}
				outIndex++;
			}
		}	
		
	}

	private BlobObject fillBlob(int pos) {
		qx[0] = pos;
		byte baseColor = getPixel(pos);
		int minX = pos % dataWidth, maxX = pos % dataWidth;
		int minY = pos / dataWidth, maxY = pos / dataWidth;
		int pixelCount = 1;
		int curX, curY, curPos;
		int centroidX = 0;

		setPixel(pos, (byte) 0);

		for (int i = 0; i < pixelCount; i++) {
			curX = qx[i] % dataWidth;
			curY = qx[i] / dataWidth;
			curPos = qx[i];

			if (curX > maxX)
				maxX = curX;
			if (curX < minX)
				minX = curX;
			if (curY > maxY)
				maxY = curY;
			if (curY < minY)
				minY = curY;

			// LEFT BREATH
			if (curX > 0 && getPixel(curPos - 1) == baseColor ){
				qx[pixelCount] = curPos - 1;
				setPixel(curPos - 1, (byte) ColorManager.UNDEFINE);
				pixelCount++;
				centroidX += curX;
			}
			
			// RIGHT BREATH
			if (curX + 1 < dataWidth && getPixel(curPos + 1) == baseColor ){
				qx[pixelCount] = curPos + 1;
				setPixel(curPos + 1, (byte) ColorManager.UNDEFINE);
				pixelCount++;
				centroidX += curX;
			}

			// DOWN BREATH
			if (curY + 1 < dataHeight && getPixel(curPos + dataWidth) == baseColor ){
				qx[pixelCount] = curPos + dataWidth;
				setPixel(curPos + dataWidth, (byte) ColorManager.UNDEFINE);
				pixelCount++;
				centroidX += curX;
			}
			// UP BREATH
			if (curY > 0 && getPixel(curPos - dataWidth) == baseColor ){
				qx[pixelCount] = curPos - dataWidth;
				setPixel(curPos - dataWidth, (byte) ColorManager.UNDEFINE);
				pixelCount++;
				centroidX += curX;
			}
		
			
		}
		centroidX /= pixelCount;

		int minSize;
		switch (baseColor) {
		case ColorManager.ORANGE:
			minSize = ColorManager.MIN_COUNT_ORANGE;
			break;
		case ColorManager.YELLOW:
			minSize = ColorManager.MIN_COUNT_YELLOW;
			break;
		case ColorManager.CYAN:
			minSize = ColorManager.MIN_COUNT_CYAN;
			break;
		case ColorManager.MAGENTA:
			minSize = ColorManager.MIN_COUNT_MAGENTA;
			break;
		default:
			minSize = 120;
			break;
		}
		
		if (pixelCount < minSize)
			return null;

		return new BlobObject(baseColor, new Rect(minX, minY, maxX, maxY),pixelCount, centroidX);
	}

	private ArrayList<BlobObject> connectBlob() {
		int i;

		BlobObject objA, objB;

		@SuppressWarnings("unchecked")
		ArrayList<BlobObject> tempList = (ArrayList<BlobObject>) GlobalVar.blobResult.clone();
		ArrayList<BlobObject> mergeList = new ArrayList<BlobObject>();

		while (tempList.size() > 0) {
			objA = tempList.get(0);
			tempList.remove(0);

			for (i = 0; i < tempList.size(); i++) {
				objB = tempList.get(i);

				if (objA.posRect.left - 4 < objB.posRect.right
						&& objA.posRect.right + 4 > objB.posRect.left
						&& objA.posRect.top - 10 < objB.posRect.bottom
						&& objA.posRect.bottom + 10 > objB.posRect.top
						&& Math.abs(objA.posRect.left - objA.posRect.right) < 70
						&& Math.abs(objB.posRect.left - objB.posRect.right) < 70) {

					if (objA.tag == objB.tag) {
						merge(objA, objB);
						tempList.remove(i);
						i--;
					}
				}
			}

			objA.tag += 10;

			int sizeA = objA.getSize();
			// sort from min to max size
			for (i = 0; i < mergeList.size(); i++)
				if (mergeList.get(i).getSize() > sizeA)
					break;
			mergeList.add(i, objA);
		}

		return mergeList;
	}

	private void merge(BlobObject a, BlobObject b) {
		a.posRect.left = Math.min(a.posRect.left, b.posRect.left);
		a.posRect.right = Math.max(a.posRect.right, b.posRect.right);
		a.posRect.top = Math.min(a.posRect.top, b.posRect.top);
		a.posRect.bottom = Math.max(a.posRect.bottom, b.posRect.bottom);
		if (a.centroidX + b.centroidX < 1)
			a.centroidX = 1;
		else
			a.centroidX = ((a.centroidX * a.pixelCount) + (b.centroidX * b.pixelCount))
					/ (a.centroidX + b.centroidX);
		a.pixelCount += b.pixelCount;

	}

	private byte getPixel(int pos) {
		return colorData[pos];
	}

	private void setPixel(int pos, byte val) {
		colorData[pos] = val;
	}

}
