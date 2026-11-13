import React from "react";
import {
  AbsoluteFill,
  Audio,
  Img,
  Sequence,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { FPS, INTRO, OUTRO, SCENES, type Scene } from "./data";

const INK = "#14100d";
const CREAM = "#f5ece0";
const MUTED = "#a8968a";
const ACCENT = "#e2764b";
const LABEL = "#c9a27e";

const SCREEN_W = 690;
const SCREEN_H = 1533;
const SCREEN_TOP = 246;
const CAPTION_TOP = 1812;
const BAR_TOP = 1884;

const sceneStarts: number[] = [];
{
  let cursor = INTRO;
  for (const scene of SCENES) {
    sceneStarts.push(cursor);
    cursor += scene.duration;
  }
}
const TOTAL = sceneStarts[sceneStarts.length - 1] + SCENES[SCENES.length - 1].duration + OUTRO;
const TOTAL_FRAMES = Math.round(TOTAL * FPS);

const Fonts: React.FC = () => (
  <style>{`
    @font-face {
      font-family: "Fraunces";
      src: url("${staticFile("fonts/fraunces.ttf")}") format("truetype");
      font-weight: 100 900;
    }
    @font-face {
      font-family: "Outfit";
      src: url("${staticFile("fonts/outfit.ttf")}") format("truetype");
      font-weight: 100 900;
    }
  `}</style>
);

const Backdrop: React.FC = () => (
  <AbsoluteFill
    style={{
      backgroundColor: INK,
      backgroundImage:
        "radial-gradient(1100px 1100px at 50% 46%, rgba(72,48,34,0.55) 0%, rgba(20,16,13,0) 68%)",
    }}
  >
    <AbsoluteFill
      style={{
        backgroundImage:
          "linear-gradient(180deg, rgba(20,16,13,0.9) 0%, rgba(20,16,13,0) 18%, rgba(20,16,13,0) 78%, rgba(20,16,13,0.95) 100%)",
      }}
    />
  </AbsoluteFill>
);

const Brand: React.FC = () => (
  <>
    <div
      style={{
        position: "absolute",
        top: 62,
        left: 68,
        fontFamily: "Fraunces",
        fontWeight: 700,
        fontSize: 54,
        letterSpacing: 5,
        color: CREAM,
      }}
    >
      ORU
    </div>
    <div
      style={{
        position: "absolute",
        top: 84,
        right: 68,
        fontFamily: "Outfit",
        fontWeight: 600,
        fontSize: 22,
        letterSpacing: 5,
        color: MUTED,
      }}
    >
      PRODUCT TOUR
    </div>
  </>
);

const Phone: React.FC<{ src: string; progress: number; visible: number }> = ({
  src,
  progress,
  visible,
}) => {
  // A whisper of a push across each scene keeps the screen alive.
  const scale = 1 + progress * 0.02;
  return (
    <div
      style={{
        position: "absolute",
        top: SCREEN_TOP,
        left: (1080 - SCREEN_W) / 2,
        width: SCREEN_W,
        height: SCREEN_H,
        borderRadius: 40,
        overflow: "hidden",
        border: "10px solid #221b16",
        boxSizing: "content-box",
        boxShadow: "0 40px 90px rgba(0,0,0,0.55), 0 2px 0 rgba(255,255,255,0.06) inset",
        opacity: visible,
        transform: `translateY(${(1 - visible) * 26}px)`,
      }}
    >
      <Img
        src={src}
        style={{
          width: "100%",
          height: "100%",
          objectFit: "cover",
          transform: `scale(${scale})`,
        }}
      />
    </div>
  );
};

const Progress: React.FC<{ active: number; fraction: number }> = ({ active, fraction }) => {
  const width = 900;
  const gap = 10;
  const seg = (width - gap * (SCENES.length - 1)) / SCENES.length;
  return (
    <div
      style={{
        position: "absolute",
        top: BAR_TOP,
        left: (1080 - width) / 2,
        display: "flex",
        gap,
      }}
    >
      {SCENES.map((_, index) => (
        <div
          key={index}
          style={{ width: seg, height: 5, borderRadius: 3, background: "#342a24", overflow: "hidden" }}
        >
          <div
            style={{
              height: "100%",
              width: index < active ? "100%" : index === active ? `${Math.min(1, fraction) * 100}%` : "0%",
              background: index === active ? CREAM : ACCENT,
              borderRadius: 3,
            }}
          />
        </div>
      ))}
    </div>
  );
};

const SceneView: React.FC<{ scene: Scene; index: number }> = ({ scene, index }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const total = Math.round(scene.duration * fps);
  const local = frame / fps;

  // Play the real capture at its own pace, then hold the last moment briefly.
  const playable = scene.duration * 0.88;
  const progress = Math.min(1, local / playable);
  const frameIndex = Math.min(scene.frames - 1, Math.floor(progress * (scene.frames - 1)));
  const src = staticFile(`frames/${scene.name}/${String(frameIndex + 1).padStart(4, "0")}.webp`);

  const enter = spring({ frame, fps, config: { damping: 200, mass: 0.7 } });
  const exit = interpolate(frame, [total - 8, total], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const visible = Math.min(enter, exit);
  const textIn = spring({ frame: frame - 8, fps, config: { damping: 200, mass: 0.8 } });
  const textOut = interpolate(frame, [total - 10, total - 2], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  return (
    <AbsoluteFill>
      <Phone src={src} progress={progress} visible={visible} />
      <div
        style={{
          position: "absolute",
          top: 164,
          left: 68,
          fontFamily: "Outfit",
          fontWeight: 700,
          fontSize: 26,
          letterSpacing: 4,
          color: LABEL,
          opacity: Math.min(textIn, textOut),
          transform: `translateY(${(1 - textIn) * 16}px)`,
        }}
      >
        {scene.label}
      </div>
      <div
        style={{
          position: "absolute",
          top: CAPTION_TOP,
          left: 60,
          right: 60,
          textAlign: "center",
          fontFamily: "Fraunces",
          fontWeight: 500,
          fontSize: 40,
          color: CREAM,
          opacity: Math.min(textIn, textOut),
          transform: `translateY(${(1 - textIn) * 16}px)`,
        }}
      >
        {scene.caption}
      </div>
      <Progress active={index} fraction={progress} />
    </AbsoluteFill>
  );
};

const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const ease = spring({ frame, fps, config: { damping: 200, mass: 0.9 } });
  const fade = interpolate(frame, [Math.round(INTRO * fps) - 10, Math.round(INTRO * fps)], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const sub = spring({ frame: frame - 10, fps, config: { damping: 200 } });
  return (
    <AbsoluteFill style={{ opacity: fade }}>
      <div
        style={{
          position: "absolute",
          width: 1080,
          top: 700,
          textAlign: "center",
          fontFamily: "Fraunces",
          fontWeight: 700,
          fontSize: 196,
          letterSpacing: 10,
          color: CREAM,
          opacity: ease,
          transform: `scale(${1.06 - 0.06 * ease})`,
        }}
      >
        ORU
      </div>
      <div
        style={{
          position: "absolute",
          width: 1080,
          top: 960,
          textAlign: "center",
          fontFamily: "Fraunces",
          fontWeight: 500,
          fontSize: 46,
          color: CREAM,
          opacity: sub,
        }}
      >
        A home for your music.
      </div>
      <div
        style={{
          position: "absolute",
          width: 1080,
          top: 1046,
          textAlign: "center",
          fontFamily: "Outfit",
          fontWeight: 600,
          fontSize: 24,
          letterSpacing: 6,
          color: MUTED,
          opacity: sub,
        }}
      >
        LISTEN / RELEASE / GROW
      </div>
    </AbsoluteFill>
  );
};

const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const total = Math.round(OUTRO * fps);
  const ease = spring({ frame, fps, config: { damping: 200, mass: 0.9 } });
  const sub = spring({ frame: frame - 10, fps, config: { damping: 200 } });
  const black = interpolate(frame, [total - 24, total - 4], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  return (
    <AbsoluteFill>
      <AbsoluteFill style={{ opacity: 1 }}>
        <div
          style={{
            position: "absolute",
            width: 1080,
            top: 580,
            textAlign: "center",
            fontFamily: "Fraunces",
            fontWeight: 700,
            fontSize: 132,
            letterSpacing: 8,
            color: CREAM,
            opacity: ease,
          }}
        >
          ORU
        </div>
        <div
          style={{
            position: "absolute",
            width: 1080,
            top: 850,
            textAlign: "center",
            fontFamily: "Fraunces",
            fontWeight: 500,
            fontSize: 76,
            lineHeight: 1.35,
            color: CREAM,
            opacity: sub,
          }}
        >
          Your music. Your audience.
          <br />
          Your ORU.
        </div>
        <div
          style={{
            position: "absolute",
            width: 1080,
            top: 1090,
            textAlign: "center",
            fontFamily: "Outfit",
            fontWeight: 500,
            fontSize: 30,
            letterSpacing: 2,
            color: LABEL,
            opacity: sub,
          }}
        >
          oru.freebuff.app
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ backgroundColor: "#14100d", opacity: black }} />
    </AbsoluteFill>
  );
};

export const Tour: React.FC = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: INK }}>
      <Fonts />
      <Backdrop />
      <Brand />

      <Sequence from={0} durationInFrames={Math.round(INTRO * FPS)}>
        <Intro />
      </Sequence>

      {SCENES.map((scene, index) => (
        <Sequence
          key={scene.name}
          from={Math.round(sceneStarts[index] * FPS)}
          durationInFrames={Math.round(scene.duration * FPS)}
        >
          <SceneView scene={scene} index={index} />
        </Sequence>
      ))}

      <Sequence
        from={Math.round((sceneStarts[sceneStarts.length - 1] + SCENES[SCENES.length - 1].duration) * FPS)}
        durationInFrames={Math.round(OUTRO * FPS)}
      >
        <Outro />
      </Sequence>

      <Audio
        src={staticFile("audio/soundtrack.mp3")}
        volume={(f) =>
          interpolate(f, [0, 30, TOTAL_FRAMES - 70, TOTAL_FRAMES], [0, 0.9, 0.9, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })
        }
      />
    </AbsoluteFill>
  );
};
