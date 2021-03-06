/*
 * Copyright (c) 2011-2018, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.geo.robust;

import boofcv.abst.geo.Triangulate2ViewsMetric;
import boofcv.alg.geo.DistanceFromModelMultiView;
import boofcv.alg.geo.NormalizedToPixelError;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.geo.AssociatedPair;
import georegression.struct.point.Point3D_F64;
import georegression.struct.se.Se3_F64;
import georegression.transform.se.SePointOps_F64;

import java.util.List;

/**
 * <p>
 * Computes the error for a given camera motion from two calibrated views.  First a point
 * is triangulated from the two views and the motion.  Then the difference between
 * the observed and projected point is found at each view. Error is normalized pixel difference
 * squared.
 * </p>
 * <p>
 * error = &Delta;x<sub>1</sub><sup>2</sup> + &Delta;y<sub>1</sub><sup>2</sup> +
 * &Delta;x<sub>2</sub><sup>2</sup> + &Delta;y<sub>2</sub><sup>2</sup>
 * </p>
 *
 * <p>
 * Error units can be in either pixels<sup>2</sup> or unit less (normalized pixel coordinates).  To compute
 * the error in pixels pass in the correct intrinsic calibration parameters in the constructor.  Otherwise
 * pass in fx=1.fy=1,skew=0 for normalized.
 * </p>
 *
 * <p>
 * NOTE: If a point does not pass the positive depth constraint then a very large error is returned.
 * </p>
 *
 * <p>
 * NOTE: The provided transform must be from the key frame into the current frame.
 * </p>
 *
 * @author Peter Abeles
 */
public class DistanceSe3SymmetricSq implements DistanceFromModelMultiView<Se3_F64,AssociatedPair> {

	// transform from key frame to current frame
	private Se3_F64 keyToCurr;
	// triangulation algorithm
	private Triangulate2ViewsMetric triangulate;
	// working storage
	private Point3D_F64 p = new Point3D_F64();

	// Used to compute error in pixels
	private NormalizedToPixelError errorCam1 = new NormalizedToPixelError();
	private NormalizedToPixelError errorCam2 = new NormalizedToPixelError();

	/**
	 * Configure distance calculation.
	 *
	 * @param triangulate Triangulates the intersection of two observations
	 */
	public DistanceSe3SymmetricSq(Triangulate2ViewsMetric triangulate ) {
		this.triangulate = triangulate;
	}

	@Override
	public void setModel(Se3_F64 keyToCurr) {
		this.keyToCurr = keyToCurr;
	}

	/**
	 * Computes the error given the motion model
	 *
	 * @param obs Observation in normalized pixel coordinates
	 * @return observation error
	 */
	@Override
	public double computeDistance(AssociatedPair obs) {

		// triangulate the point in 3D space
		triangulate.triangulate(obs.p1,obs.p2,keyToCurr,p);

		if( p.z < 0 )
			return Double.MAX_VALUE;

		// compute observational error in each view
		double error = errorCam1.errorSq(obs.p1.x,obs.p1.y,p.x/p.z,p.y/p.z);

		SePointOps_F64.transform(keyToCurr,p,p);
		if( p.z < 0 )
			return Double.MAX_VALUE;

		error += errorCam2.errorSq(obs.p2.x,obs.p2.y, p.x/p.z , p.y/p.z);

		return error;
	}

	@Override
	public void computeDistance(List<AssociatedPair> associatedPairs, double[] distance) {
		for( int i = 0; i < associatedPairs.size(); i++ ) {
			AssociatedPair obs = associatedPairs.get(i);
			distance[i] = computeDistance(obs);
		}
	}

	@Override
	public Class<AssociatedPair> getPointType() {
		return AssociatedPair.class;
	}

	@Override
	public Class<Se3_F64> getModelType() {
		return Se3_F64.class;
	}

	@Override
	public void setIntrinsic( int view, CameraPinhole intrinsic) {
		if( view == 0 )
			errorCam1.set(intrinsic.fx,intrinsic.fy,intrinsic.skew);
		else if( view == 1 )
			errorCam2.set(intrinsic.fx,intrinsic.fy,intrinsic.skew);
		else
			throw new IllegalArgumentException("View must be 0 or 1");
	}

	@Override
	public int getNumberOfViews() {
		return 2;
	}

}
