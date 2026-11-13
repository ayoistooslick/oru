import React from "react";
import { Composition } from "remotion";
import { INTRO, OUTRO, SCENES } from "./data";
import { Tour } from "./Tour";

const seconds = INTRO + SCENES.reduce((sum, scene) => sum + scene.duration, 0) + OUTRO;

export const RemotionRoot: React.FC = () => (
  <Composition
    id="Tour"
    component={Tour}
    durationInFrames={Math.round(seconds * 30)}
    fps={30}
    width={1080}
    height={1920}
  />
);
