package buttondevteam.townyplotresizer;

import java.util.function.Function;

@FunctionalInterface
public interface SetValueFunction {
	public void setValue(String path, Function<String, Number> parser, boolean isfloat, boolean isprice);
}
