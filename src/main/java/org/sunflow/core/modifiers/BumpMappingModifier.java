package org.sunflow.core.modifiers;

import org.sunflow.SunflowAPI;
import org.sunflow.core.Modifier;
import org.sunflow.core.ParameterList;
import org.sunflow.core.ShadingState;
import org.sunflow.core.Texture;
import org.sunflow.math.OrthoNormalBasis;

public class BumpMappingModifier implements Modifier {
    private Texture bumpTexture;
    private float scale;

    public BumpMappingModifier() {
        bumpTexture = null;
        scale = 1;
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        String filename = pl.getString("texture", null);
        if (filename != null)
            // EP : Made texture cache local to a SunFlow API instance
            bumpTexture = api.getTextureCache().getTexture(api.resolveTextureFilename(filename), true);
        scale = pl.getFloat("scale", scale);
        return bumpTexture != null;
    }

    public void modify(ShadingState state) {
        // apply bump
        state.getNormal().set(bumpTexture.getBump(state.getUV().x, state.getUV().y, state.getBasis(), scale));
        state.setBasis(OrthoNormalBasis.makeFromW(state.getNormal()));
    }
}