package com.tacz.legacy.api.client.animation;

import com.tacz.legacy.api.client.animation.interpolator.CustomInterpolator;
import com.tacz.legacy.api.client.animation.interpolator.InterpolatorUtil;
import com.tacz.legacy.client.resource.pojo.animation.bedrock.*;
import com.tacz.legacy.client.resource.gltf.GltfAnimationData;
import com.tacz.legacy.util.math.MathUtil;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;
import net.minecraft.util.ResourceLocation;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Animations {
    public static AnimationController createControllerFromBedrock(BedrockAnimationFile animationFile, AnimationListenerSupplier supplier) {
        return new AnimationController(createAnimationFromBedrock(animationFile), supplier);
    }

    public static AnimationController createControllerFromGltf(GltfAnimationData animationData, AnimationListenerSupplier supplier) {
        List<ObjectAnimation> result = new ArrayList<>();
        for (GltfAnimationData.Animation animationModel : animationData.getAnimations()) {
            ObjectAnimation animation = new ObjectAnimation(animationModel.getName());
            for (GltfAnimationData.Channel channelModel : animationModel.getChannels()) {
                ObjectAnimationChannel channel = new ObjectAnimationChannel(ObjectAnimationChannel.ChannelType.valueOf(channelModel.getPath().name()));
                if (channel.type == ObjectAnimationChannel.ChannelType.ROTATION
                        && channelModel.getInterpolation() == GltfAnimationData.Interpolation.LINEAR) {
                    channel.interpolator = InterpolatorUtil.fromInterpolation(InterpolatorUtil.InterpolatorType.SLERP);
                } else {
                    switch (channelModel.getInterpolation()) {
                        case STEP:
                            channel.interpolator = InterpolatorUtil.fromInterpolation(InterpolatorUtil.InterpolatorType.STEP);
                            break;
                        case SPLINE:
                            channel.interpolator = InterpolatorUtil.fromInterpolation(InterpolatorUtil.InterpolatorType.SPLINE);
                            break;
                        case LINEAR:
                        default:
                            channel.interpolator = InterpolatorUtil.fromInterpolation(InterpolatorUtil.InterpolatorType.LINEAR);
                            break;
                    }
                }
                channel.node = channelModel.getNodeName();

                AnimationListener animationListener = supplier.supplyListeners(channel.node, channel.type);
                if (animationListener == null) {
                    continue;
                }
                float[] inverseValue = Arrays.copyOf(animationListener.initialValue(), animationListener.initialValue().length);
                float[] keyframeTimeS = Arrays.copyOf(channelModel.getKeyframeTimeS(), channelModel.getKeyframeTimeS().length);
                float[][] values = new float[channelModel.getValues().length][];

                if (channel.type == ObjectAnimationChannel.ChannelType.ROTATION) {
                    if (inverseValue.length == 3) {
                        inverseValue = MathUtil.toQuaternion(inverseValue[0], inverseValue[1], inverseValue[2]);
                    }
                    inverseValue = MathUtil.inverseQuaternion(inverseValue);
                } else if (channel.type == ObjectAnimationChannel.ChannelType.TRANSLATION && inverseValue.length >= 3) {
                    inverseValue[0] = -inverseValue[0];
                    inverseValue[1] = -inverseValue[1];
                    inverseValue[2] = -inverseValue[2];
                }

                for (int i = 0; i < channelModel.getValues().length; i++) {
                    values[i] = Arrays.copyOf(channelModel.getValues()[i], channelModel.getValues()[i].length);
                    switch (channel.type) {
                        case ROTATION:
                            if (inverseValue.length >= 4) {
                                for (int offset = 0; offset + 3 < values[i].length; offset += 4) {
                                    float[] rawQuaternion = Arrays.copyOfRange(values[i], offset, offset + 4);
                                    float[] rawEuler = MathUtil.toEulerAngles(rawQuaternion);
                                    float[] legacyQuaternion = MathUtil.toQuaternion(-rawEuler[0], -rawEuler[1], rawEuler[2]);
                                    float[] resultQuaternion = MathUtil.mulQuaternion(inverseValue, legacyQuaternion);
                                    System.arraycopy(resultQuaternion, 0, values[i], offset, 4);
                                }
                            }
                            break;
                        case TRANSLATION:
                            if (inverseValue.length >= 3) {
                                for (int offset = 0; offset + 2 < values[i].length; offset += 3) {
                                    values[i][offset] = -values[i][offset] + inverseValue[0];
                                    values[i][offset + 1] = -(-values[i][offset + 1] + inverseValue[1]);
                                    values[i][offset + 2] = values[i][offset + 2] + inverseValue[2];
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }

                channel.content.keyframeTimeS = keyframeTimeS;
                channel.content.values = values;
                channel.interpolator.compile(channel.content);
                animation.addChannel(channel);
            }
            result.add(animation);
        }
        return new AnimationController(result, supplier);
    }

    public static @Nonnull List<ObjectAnimation> createAnimationFromBedrock(BedrockAnimationFile animationFile) {
        List<ObjectAnimation> result = new ArrayList<>();
        for (Map.Entry<String, BedrockAnimation> animationEntry : animationFile.getAnimations().entrySet()) {
            ObjectAnimation animation = new ObjectAnimation(animationEntry.getKey());
            BedrockAnimation bedrockAnimation = animationEntry.getValue();
            if (bedrockAnimation.getBones() != null) {
                for (Map.Entry<String, AnimationBone> boneEntry : bedrockAnimation.getBones().entrySet()) {
                    AnimationBone bone = boneEntry.getValue();
                    AnimationKeyframes translationKeyframes = bone.getPosition();
                    AnimationKeyframes rotationKeyframes = bone.getRotation();
                    AnimationKeyframes scaleKeyframes = bone.getScale();
                    if (translationKeyframes != null) {
                        ObjectAnimationChannel translationChannel = new ObjectAnimationChannel(ObjectAnimationChannel.ChannelType.TRANSLATION);
                        translationChannel.node = boneEntry.getKey();
                        translationChannel.interpolator = new CustomInterpolator();
                        writeBedrockTranslation(translationChannel, bone.getPosition());
                        translationChannel.interpolator.compile(translationChannel.content);
                        animation.addChannel(translationChannel);
                    }
                    if (rotationKeyframes != null) {
                        ObjectAnimationChannel rotationChannel = new ObjectAnimationChannel(ObjectAnimationChannel.ChannelType.ROTATION);
                        rotationChannel.node = boneEntry.getKey();
                        rotationChannel.interpolator = new CustomInterpolator();
                        writeBedrockRotation(rotationChannel, bone.getRotation());
                        rotationChannel.interpolator.compile(rotationChannel.content);
                        animation.addChannel(rotationChannel);
                    }
                    if (scaleKeyframes != null) {
                        ObjectAnimationChannel scaleChannel = new ObjectAnimationChannel(ObjectAnimationChannel.ChannelType.SCALE);
                        scaleChannel.node = boneEntry.getKey();
                        scaleChannel.interpolator = new CustomInterpolator();
                        writeBedrockScale(scaleChannel, bone.getScale());
                        scaleChannel.interpolator.compile(scaleChannel.content);
                        animation.addChannel(scaleChannel);
                    }
                }
            }
            // 将声音数据转移到 ObjectAnimation 中
            SoundEffectKeyframes soundEffectKeyframes = bedrockAnimation.getSoundEffects();
            if (soundEffectKeyframes != null) {
                ObjectAnimationSoundChannel soundChannel = new ObjectAnimationSoundChannel();
                soundChannel.content = new AnimationSoundChannelContent();
                int keyframeNum = soundEffectKeyframes.getKeyframes().size();
                soundChannel.content.keyframeTimeS = new double[keyframeNum];
                soundChannel.content.keyframeSoundName = new ResourceLocation[keyframeNum];
                int i = 0;
                for (Map.Entry<Double, ResourceLocation> entry : soundEffectKeyframes.getKeyframes().double2ObjectEntrySet()) {
                    soundChannel.content.keyframeTimeS[i] = entry.getKey();
                    soundChannel.content.keyframeSoundName[i] = entry.getValue();
                    i++;
                }
                animation.setSoundChannel(soundChannel);
            }
            result.add(animation);
        }
        return result;
    }

    private static void writeBedrockTranslation(ObjectAnimationChannel animationChannel, AnimationKeyframes keyframes) {
        Double2ObjectRBTreeMap<AnimationKeyframes.Keyframe> keyframesMap = keyframes.getKeyframes();
        animationChannel.content.keyframeTimeS = new float[keyframesMap.size()];
        animationChannel.content.values = new float[keyframesMap.size()][];
        animationChannel.content.lerpModes = new AnimationChannelContent.LerpMode[keyframesMap.size()];
        int index = 0;
        for (Double2ObjectMap.Entry<AnimationKeyframes.Keyframe> entry : keyframesMap.double2ObjectEntrySet()) {
            animationChannel.content.keyframeTimeS[index] = (float) entry.getDoubleKey();
            AnimationKeyframes.Keyframe keyframe = entry.getValue();
            if (keyframe.pre() != null || keyframe.post() != null) {
                if (keyframe.pre() != null && keyframe.post() != null) {
                    animationChannel.content.values[index] = new float[6];
                    Vector3f pre = new Vector3f(keyframe.pre());
                    Vector3f post = new Vector3f(keyframe.post());
                    pre.mul(1 / 16f, 1 / 16f, 1 / 16f);
                    post.mul(1 / 16f, 1 / 16f, 1 / 16f);
                    readVector3fToArray(animationChannel.content.values[index], pre, 0);
                    readVector3fToArray(animationChannel.content.values[index], post, 3);
                } else if (keyframe.pre() != null) {
                    animationChannel.content.values[index] = new float[3];
                    Vector3f pre = new Vector3f(keyframe.pre());
                    pre.mul(1 / 16f, 1 / 16f, 1 / 16f);
                    readVector3fToArray(animationChannel.content.values[index], pre, 0);
                } else {
                    animationChannel.content.values[index] = new float[3];
                    Vector3f post = new Vector3f(keyframe.post());
                    post.mul(1 / 16f, 1 / 16f, 1 / 16f);
                    readVector3fToArray(animationChannel.content.values[index], post, 0);
                }
            } else if (keyframe.data() != null) {
                animationChannel.content.values[index] = new float[3];
                Vector3f data = new Vector3f(keyframe.data());
                data.mul(1 / 16f, 1 / 16f, 1 / 16f);
                readVector3fToArray(animationChannel.content.values[index], data, 0);
            }
            String lerpModeName = keyframe.lerpMode();
            if (lerpModeName != null) {
                try {
                    animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.valueOf(lerpModeName.toUpperCase(Locale.ENGLISH));
                } catch (IllegalArgumentException e) {
                    animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.LINEAR;
                }
            } else {
                animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.LINEAR;
            }
            index++;
        }
    }

    private static void writeBedrockRotation(ObjectAnimationChannel animationChannel, AnimationKeyframes keyframes) {
        Double2ObjectRBTreeMap<AnimationKeyframes.Keyframe> keyframesMap = keyframes.getKeyframes();
        animationChannel.content.keyframeTimeS = new float[keyframesMap.size()];
        animationChannel.content.values = new float[keyframesMap.size()][];
        animationChannel.content.lerpModes = new AnimationChannelContent.LerpMode[keyframesMap.size()];
        int index = 0;
        for (Double2ObjectMap.Entry<AnimationKeyframes.Keyframe> entry : keyframesMap.double2ObjectEntrySet()) {
            animationChannel.content.keyframeTimeS[index] = (float) entry.getDoubleKey();
            AnimationKeyframes.Keyframe keyframe = entry.getValue();
            if (keyframe.pre() != null || keyframe.post() != null) {
                if (keyframe.pre() != null && keyframe.post() != null) {
                    animationChannel.content.values[index] = new float[6];
                    Vector3f pre = new Vector3f(keyframe.pre());
                    Vector3f post = new Vector3f(keyframe.post());
                    toAngle(pre);
                    toAngle(post);
                    animationChannel.content.values[index][0] = pre.x();
                    animationChannel.content.values[index][1] = pre.y();
                    animationChannel.content.values[index][2] = pre.z();
                    animationChannel.content.values[index][3] = post.x();
                    animationChannel.content.values[index][4] = post.y();
                    animationChannel.content.values[index][5] = post.z();
                } else if (keyframe.pre() != null) {
                    animationChannel.content.values[index] = new float[3];
                    Vector3f pre = new Vector3f(keyframe.pre());
                    toAngle(pre);
                    animationChannel.content.values[index][0] = pre.x();
                    animationChannel.content.values[index][1] = pre.y();
                    animationChannel.content.values[index][2] = pre.z();
                } else {
                    animationChannel.content.values[index] = new float[3];
                    Vector3f post = new Vector3f(keyframe.post());
                    toAngle(post);
                    animationChannel.content.values[index][0] = post.x();
                    animationChannel.content.values[index][1] = post.y();
                    animationChannel.content.values[index][2] = post.z();
                }
            } else if (keyframe.data() != null) {
                animationChannel.content.values[index] = new float[3];
                Vector3f data = new Vector3f(keyframe.data());
                toAngle(data);
                animationChannel.content.values[index][0] = data.x();
                animationChannel.content.values[index][1] = data.y();
                animationChannel.content.values[index][2] = data.z();
            }
            String lerpModeName = keyframe.lerpMode();
            if (lerpModeName != null) {
                if (lerpModeName.equals(AnimationChannelContent.LerpMode.CATMULLROM.name().toLowerCase())) {
                    animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.CATMULLROM;
                } else {
                    animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.LINEAR;
                }
            } else {
                animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.LINEAR;
            }
            index++;
        }
    }

    private static void writeBedrockScale(ObjectAnimationChannel animationChannel, AnimationKeyframes keyframes) {
        Double2ObjectRBTreeMap<AnimationKeyframes.Keyframe> keyframesMap = keyframes.getKeyframes();
        animationChannel.content.keyframeTimeS = new float[keyframesMap.size()];
        animationChannel.content.values = new float[keyframesMap.size()][];
        animationChannel.content.lerpModes = new AnimationChannelContent.LerpMode[keyframesMap.size()];
        int index = 0;
        for (Double2ObjectMap.Entry<AnimationKeyframes.Keyframe> entry : keyframesMap.double2ObjectEntrySet()) {
            animationChannel.content.keyframeTimeS[index] = (float) entry.getDoubleKey();
            AnimationKeyframes.Keyframe keyframe = entry.getValue();
            if (keyframe.pre() != null || keyframe.post() != null) {
                if (keyframe.pre() != null && keyframe.post() != null) {
                    animationChannel.content.values[index] = new float[6];
                    Vector3f pre = keyframe.pre();
                    Vector3f post = keyframe.post();
                    readVector3fToArray(animationChannel.content.values[index], pre, 0);
                    readVector3fToArray(animationChannel.content.values[index], post, 3);
                } else if (keyframe.pre() != null) {
                    animationChannel.content.values[index] = new float[3];
                    Vector3f pre = keyframe.pre();
                    readVector3fToArray(animationChannel.content.values[index], pre, 0);
                } else {
                    animationChannel.content.values[index] = new float[3];
                    Vector3f post = keyframe.post();
                    readVector3fToArray(animationChannel.content.values[index], post, 0);
                }
            } else if (keyframe.data() != null) {
                animationChannel.content.values[index] = new float[3];
                Vector3f data = keyframe.data();
                readVector3fToArray(animationChannel.content.values[index], data, 0);
            }
            String lerpModeName = keyframe.lerpMode();
            if (lerpModeName != null) {
                try {
                    animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.valueOf(lerpModeName.toUpperCase(Locale.ENGLISH));
                } catch (IllegalArgumentException e) {
                    animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.LINEAR;
                }
            } else {
                animationChannel.content.lerpModes[index] = AnimationChannelContent.LerpMode.LINEAR;
            }
            index++;
        }
    }

    private static void toAngle(Vector3f vector3f) {
        vector3f.set((float) Math.toRadians(vector3f.x()), (float) Math.toRadians(vector3f.y()), (float) Math.toRadians(vector3f.z()));
    }

    private static void readVector3fToArray(float[] array, Vector3f vector3f, int offset) {
        array[offset] = vector3f.x();
        array[offset + 1] = vector3f.y();
        array[offset + 2] = vector3f.z();
    }
}
