package com.jfinal.template;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.jfinal.kit.Kv;

public class EngineTest {
	
	Engine engine;
	
	@Before
	public void init() {
		engine = Engine.use();
		engine.setToClassPathSourceFactory();
	}
	
	@After
	public void exit() {
	}
	
	@Test
	public void renderToString() {
		Kv para = Kv.by("key", "value");
		String result = Engine.use().getTemplateByString("#(key)").renderToString(para);
		Assert.assertEquals("value", result);
	}
}
