import React from "react";
import { channelColor, initial } from "../media";

function ChannelAvatar({ name, size = 32 }) {
  return (
    <span
      className="channel-avatar"
      style={{ background: channelColor(name), width: size, height: size, fontSize: size * 0.42 }}
      aria-hidden="true"
    >
      {initial(name)}
    </span>
  );
}

export default ChannelAvatar;
