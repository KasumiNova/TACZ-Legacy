package com.tacz.legacy.util.math;

import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;

/**
 * Ported from upstream TACZ MathUtil — quaternion, slerp, spline, and euler angle utilities
 * used by the animation runtime.
 */
public class MathUtil {
    public static final float[] QUATERNION_ONE = {0, 0, 0, 1};

    /**
     * 按照 z(roll) -> y(yaw) -> x(pitch) 的旋转顺序，求四元数。
     * @param pitch 绕 x 轴旋转的弧度
     * @param yaw 绕 y 轴旋转的弧度
     * @param roll 绕 z 轴旋转的弧度
     * @return 四元数，前三个数是虚部，最后一个数是实部。
     */
    public static float[] toQuaternion(float pitch, float yaw, float roll) {
        double cy = Math.cos(roll * 0.5);
        double sy = Math.sin(roll * 0.5);
        double cp = Math.cos(yaw * 0.5);
        double sp = Math.sin(yaw * 0.5);
        double cr = Math.cos(pitch * 0.5);
        double sr = Math.sin(pitch * 0.5);
        return new float[]{
                (float) (cy * cp * sr - sy * sp * cr),
                (float) (sy * cp * sr + cy * sp * cr),
                (float) (sy * cp * cr - cy * sp * sr),
                (float) (cy * cp * cr + sy * sp * sr)
        };
    }

    public static void toQuaternion(float pitch, float yaw, float roll, @Nonnull Quaternionf quaternion) {
        double cy = Math.cos(roll * 0.5);
        double sy = Math.sin(roll * 0.5);
        double cp = Math.cos(yaw * 0.5);
        double sp = Math.sin(yaw * 0.5);
        double cr = Math.cos(pitch * 0.5);
        double sr = Math.sin(pitch * 0.5);

        quaternion.set(
                (float) (cy * cp * sr - sy * sp * cr),
                (float) (sy * cp * sr + cy * sp * cr),
                (float) (sy * cp * cr - cy * sp * sr),
                (float) (cy * cp * cr + sy * sp * sr)
        );
    }

    public static float[] toEulerAngles(Quaternionf q) {
        float[] angles = new float[3];
        double sinrCosp = 2 * (q.w() * q.x() + q.y() * q.z());
        double cosrCosp = 1 - 2 * (q.x() * q.x() + q.y() * q.y());
        angles[0] = (float) Math.atan2(sinrCosp, cosrCosp);
        double sinp = 2 * (q.w() * q.y() - q.x() * q.z());
        if (Math.abs(sinp) >= 1) {
            angles[1] = (float) copySign(Math.PI / 2, sinp);
        } else {
            angles[1] = (float) Math.asin(sinp);
        }
        double sinyCosp = 2 * (q.w() * q.z() + q.y() * q.x());
        double cosyCosp = 1 - 2 * (q.y() * q.y() + q.z() * q.z());
        angles[2] = (float) Math.atan2(sinyCosp, cosyCosp);
        return angles;
    }

    public static float[] toEulerAngles(float[] q) {
        float[] angles = new float[3];
        double sinrCosp = 2 * (q[3] * q[0] + q[1] * q[2]);
        double cosrCosp = 1 - 2 * (q[0] * q[0] + q[1] * q[1]);
        angles[0] = (float) Math.atan2(sinrCosp, cosrCosp);
        double sinp = 2 * (q[3] * q[1] - q[2] * q[0]);
        if (Math.abs(sinp) >= 1) {
            angles[1] = (float) copySign(Math.PI / 2, sinp);
        } else {
            angles[1] = (float) Math.asin(sinp);
        }
        double sinyCosp = 2 * (q[3] * q[2] + q[1] * q[0]);
        double cosyCosp = 1 - 2 * (q[1] * q[1] + q[2] * q[2]);
        angles[2] = (float) Math.atan2(sinyCosp, cosyCosp);
        return angles;
    }

    public static double copySign(double magnitude, double sign) {
        return Math.abs(magnitude) * (sign < 0 ? -1 : 1);
    }

    /**
     * 将负旋转角(弧度)转换为等效的正角(角度)
     * @param angle 弧度
     * @return 等效正角(角度)
     */
    public static double toDegreePositive(double angle) {
        while (angle < 0) {
            angle += Math.PI * 2;
        }
        return Math.toDegrees(angle);
    }

    public static float[] inverseQuaternion(float[] quaternion) {
        float[] result = new float[4];
        result[0] = -quaternion[0];
        result[1] = -quaternion[1];
        result[2] = -quaternion[2];
        result[3] = quaternion[3];
        float m2 = quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1] + quaternion[2] * quaternion[2] + quaternion[3] * quaternion[3];
        result[0] = result[0] / m2;
        result[1] = result[1] / m2;
        result[2] = result[2] / m2;
        result[3] = result[3] / m2;
        return result;
    }

    public static float[] mulQuaternion(float[] q1, float[] q2) {
        return new float[]{
                fma(q1[3], q2[0], fma(q1[0], q2[3], fma(q1[1], q2[2], -q1[2] * q2[1]))),
                fma(q1[3], q2[1], fma(-q1[0], q2[2], fma(q1[1], q2[3], q1[2] * q2[0]))),
                fma(q1[3], q2[2], fma(q1[0], q2[1], fma(-q1[1], q2[0], q1[2] * q2[3]))),
                fma(q1[3], q2[3], fma(-q1[0], q2[0], fma(-q1[1], q2[1], -q1[2] * q2[2])))
        };
    }

    /**
     * Java 8 compatible fused multiply-add fallback
     */
    private static float fma(float a, float b, float c) {
        return a * b + c;
    }

    public static void blendQuaternion(Quaternionf to, Quaternionf from) {
        Quaternionf q1 = new Quaternionf(to);
        Quaternionf q2 = new Quaternionf(from);
        normalizeQuaternion(q1);
        normalizeQuaternion(q2);
        logQuaternion(q1);
        logQuaternion(q2);
        q1.set(q1.x() + q2.x(), q1.y() + q2.y(), q1.z() + q2.z(), q1.w() + q2.w());
        expQuaternion(q1);
        normalizeQuaternion(q1);
        to.set(q1.x(), q1.y(), q1.z(), q1.w());
    }

    public static void normalizeQuaternion(Quaternionf q) {
        float f = q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w();
        if (f > 0) {
            float f1 = (float) MathHelper.fastInvSqrt(f);
            q.set(f1 * q.x(), f1 * q.y(), f1 * q.z(), f1 * q.w());
        } else {
            q.set(0, 0, 0, 1);
        }
    }

    public static void logQuaternion(Quaternionf q) {
        double norm = Math.sqrt(q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w());
        double vec = Math.sqrt(q.x() * q.x() + q.y() * q.y() + q.z() * q.z());
        double i = q.w() / norm;
        if (i > 1) i = 1;
        if (i < -1) i = -1;
        double theta = Math.acos(i);
        double factor = vec == 0 ? 0 : theta / vec;
        q.set(
                (float) (q.x() * factor),
                (float) (q.y() * factor),
                (float) (q.z() * factor),
                (float) Math.log(norm)
        );
    }

    public static void expQuaternion(Quaternionf q) {
        double magnitude = Math.sqrt(q.x() * q.x() + q.y() * q.y() + q.z() * q.z());
        double expW = Math.exp(q.w());
        double sinMagnitude = Math.sin(magnitude);
        double coef = magnitude == 0 ? 0 : expW * sinMagnitude / magnitude;
        q.set(
                (float) (coef * q.x()),
                (float) (coef * q.y()),
                (float) (coef * q.z()),
                (float) (expW * Math.cos(magnitude))
        );
    }

    public static float[] slerp(float[] from, float[] to, float alpha) {
        float ax = from[0];
        float ay = from[1];
        float az = from[2];
        float aw = from[3];
        float bx = to[0];
        float by = to[1];
        float bz = to[2];
        float bw = to[3];

        float dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0) {
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
            dot = -dot;
        }
        float epsilon = 1e-6f;
        float s0, s1;
        if ((1.0 - dot) > epsilon) {
            float omega = (float) Math.acos(dot);
            float invSinOmega = 1.0f / (float) Math.sin(omega);
            s0 = (float) Math.sin((1.0 - alpha) * omega) * invSinOmega;
            s1 = (float) Math.sin(alpha * omega) * invSinOmega;
        } else {
            s0 = 1.0f - alpha;
            s1 = alpha;
        }
        float[] result = new float[4];
        result[0] = s0 * ax + s1 * bx;
        result[1] = s0 * ay + s1 * by;
        result[2] = s0 * az + s1 * bz;
        result[3] = s0 * aw + s1 * bw;
        return result;
    }

    public static Quaternionf toQuaternion(float[] q) {
        return new Quaternionf(q[0], q[1], q[2], q[3]);
    }

    public static Quaternionf slerp(Quaternionf from, Quaternionf to, float alpha) {
        float ax = from.x();
        float ay = from.y();
        float az = from.z();
        float aw = from.w();
        float bx = to.x();
        float by = to.y();
        float bz = to.z();
        float bw = to.w();

        float dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0) {
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
            dot = -dot;
        }
        float epsilon = 1e-6f;
        float s0, s1;
        if ((1.0 - dot) > epsilon) {
            float omega = (float) Math.acos(dot);
            float invSinOmega = 1.0f / (float) Math.sin(omega);
            s0 = (float) Math.sin((1.0 - alpha) * omega) * invSinOmega;
            s1 = (float) Math.sin(alpha * omega) * invSinOmega;
        } else {
            s0 = 1.0f - alpha;
            s1 = alpha;
        }
        float rx = s0 * ax + s1 * bx;
        float ry = s0 * ay + s1 * by;
        float rz = s0 * az + s1 * bz;
        float rw = s0 * aw + s1 * bw;
        return new Quaternionf(rx, ry, rz, rw);
    }

    public static float splineCurve(float[] y, float tension, float alpha) {
        if (y.length != 4) {
            throw new IllegalArgumentException("y value length must be 4 when doing catmull-rom spline");
        }
        if (tension < 0 || tension > 1) {
            throw new IllegalArgumentException("tension must be 0~1 when doing catmull-rom spline");
        }
        float v0 = (y[2] - y[0]) * 0.5f;
        float v1 = (y[3] - y[1]) * 0.5f;
        float t2 = alpha * alpha;
        float t3 = alpha * t2;
        float h1 = 2f * t3 - 3f * t2 + 1f;
        float h2 = -2f * t3 + 3f * t2;
        float h3 = t3 - 2f * t2 + alpha;
        float h4 = t3 - t2;
        return h1 * y[1] + h2 * y[2] + h3 * v0 + h4 * v1;
    }

    public static float[] quaternionSplineCurve(float[][] quaternions, float tension, float alpha) {
        if (quaternions.length != 4) {
            throw new IllegalArgumentException("y value length must be 4 when doing catmull-rom spline");
        }
        if (tension < 0 || tension > 1) {
            throw new IllegalArgumentException("tension must be 0~1 when doing catmull-rom spline");
        }
        float[] angles0 = toEulerAngles(quaternions[0]);
        float[] angles1 = toEulerAngles(quaternions[1]);
        float[] angles2 = toEulerAngles(quaternions[2]);
        float[] angles3 = toEulerAngles(quaternions[3]);
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            float[] input = new float[]{angles0[i], angles1[i], angles2[i], angles3[i]};
            result[i] = splineCurve(input, tension, alpha);
        }
        return toQuaternion(result[0], result[1], result[2]);
    }

    /**
     * Get angle and axis from a quaternion.
     * Returns [angle, axisX, axisY, axisZ].
     */
    public static float[] getAngleAndAxis(float[] quaternion) {
        double angle = 2 * Math.acos(quaternion[3]);
        double sin = Math.sin(angle / 2);
        if (sin == 0) {
            return new float[]{0f, 0, 0, 0};
        }
        float axisX = (float) (quaternion[0] / sin);
        float axisY = (float) (quaternion[1] / sin);
        float axisZ = (float) (quaternion[2] / sin);
        return new float[]{(float) angle, axisX, axisY, axisZ};
    }

    public static float[] multiplyQuaternion(float[] quaternion, float multiplier) {
        float[] angleAxis = getAngleAndAxis(quaternion);
        float newAngle = angleAxis[0] * multiplier;
        double sin = Math.sin(newAngle / 2);
        double cos = Math.cos(newAngle / 2);
        return new float[]{
                (float) (angleAxis[1] * sin),
                (float) (angleAxis[2] * sin),
                (float) (angleAxis[3] * sin),
                (float) cos
        };
    }

    public static Quaternionf multiplyQuaternion(Quaternionf quaternion, float multiplier) {
        float[] arr = multiplyQuaternion(
                new float[]{quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w()},
                multiplier
        );
        return new Quaternionf(arr[0], arr[1], arr[2], arr[3]);
    }

    public static Vector3f getEulerAngles(Matrix4f matrix) {
        Vector3f dest = new Vector3f();
        matrix.getEulerAnglesZYX(dest);
        return dest;
    }

    public static float[] solveEquations(float[][] coefficients, float[] constants) {
        int n = constants.length;
        for (int pivot = 0; pivot < n - 1; pivot++) {
            for (int row = pivot + 1; row < n; row++) {
                float factor = coefficients[row][pivot] / coefficients[pivot][pivot];
                for (int col = pivot; col < n; col++) {
                    coefficients[row][col] -= coefficients[pivot][col] * factor;
                }
                constants[row] -= constants[pivot] * factor;
            }
        }
        float[] solution = new float[n];
        for (int i = n - 1; i >= 0; i--) {
            float sum = 0.0f;
            for (int j = i + 1; j < n; j++) {
                sum += coefficients[i][j] * solution[j];
            }
            solution[i] = (constants[i] - sum) / coefficients[i][i];
        }
        return solution;
    }

    public static float[] getRelativeQuaternion(float[] qa, float[] qb) {
        float[][] coefficients = {
                {qa[3], -qa[2], qa[1], qa[0]},
                {qa[2], qa[3], -qa[0], qa[1]},
                {-qa[1], qa[0], qa[3], qa[2]},
                {-qa[0], -qa[1], -qa[2], qa[3]},
        };
        float[] constants = {qb[0], qb[1], qb[2], qb[3]};
        return solveEquations(coefficients, constants);
    }

    /**
     * Interpolate between two transform matrices (rotation + translation).
     * Port of upstream TACZ MathUtil.applyMatrixLerp.
     */
    public static void applyMatrixLerp(Matrix4f fromMatrix, Matrix4f toMatrix, Matrix4f resultMatrix, float alpha) {
        Vector3f translation = new Vector3f(
                toMatrix.m30() - fromMatrix.m30(),
                toMatrix.m31() - fromMatrix.m31(),
                toMatrix.m32() - fromMatrix.m32()
        );
        translation.mul(alpha);
        Vector3f fromRotation = getEulerAngles(fromMatrix);
        float[] qFrom = toQuaternion(fromRotation.x(), fromRotation.y(), fromRotation.z());
        Vector3f toRotation = getEulerAngles(toMatrix);
        float[] qTo = toQuaternion(toRotation.x(), toRotation.y(), toRotation.z());
        float[] qRelative = getRelativeQuaternion(qFrom, qTo);
        Quaternionf qLerped = toQuaternion(slerp(QUATERNION_ONE, qRelative, alpha));
        resultMatrix.m30(resultMatrix.m30() + translation.x());
        resultMatrix.m31(resultMatrix.m31() + translation.y());
        resultMatrix.m32(resultMatrix.m32() + translation.z());
        resultMatrix.rotate(qLerped);
    }
}
