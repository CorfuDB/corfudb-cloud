'use strict';

function Welcome(props) {
    const {register, control, handleSubmit} = ReactHookForm.useForm({
        defaultValues: {
            test: [{name: "server1", url: "http://example.com/archive.tgz"}]
        }
    });
    const { fields, append, prepend, remove, swap, move, insert } = ReactHookForm.useFieldArray(
        {
            control,
            name: "test"
        }
    );

    const onSubmit = data => {
        /*const requestOptions = {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        };*/

        console.log(data)

        //fetch('/processing/' + data.aggregationUnit, requestOptions)
          //  .then(response => response.json())
            //.then(response => this.setState({ postId: response.id }));
    }

    return (
        <form onSubmit={handleSubmit(onSubmit)}>
            <h4>Archives</h4>

            <label>Aggregation unit: </label>
            <input
                name={`aggregationUnit`}
                ref={register()}
                size={30}
            />
            <br/>
            <br/>

            <section>
                <button
                    type="button"
                    onClick={() => {
                        append({name: "server", url: "http://example.com/arhive.tgz"});
                    }}
                >
                    Append
                </button>
            </section>

            <ul>
                {fields.map((item, index) => {
                    return (
                        <li key={item.id}>
                            <input
                                name={`archives[${index}].name`}
                                defaultValue={`${item.name}`} // make sure to set up defaultValue
                                ref={register()}
                            />

                            <ReactHookForm.Controller
                                as={<input/>}
                                name={`archives[${index}].url`}
                                size={70}
                                control={control}
                                defaultValue={item.url} // make sure to set up defaultValue
                            />
                            <button type="button" onClick={() => remove(index)}>
                                Delete
                            </button>
                        </li>
                    );
                })}
            </ul>

            <input type="submit"/>
        </form>
    );
}

ReactDOM.render(
    <Welcome/>,
    document.getElementById('root')
);